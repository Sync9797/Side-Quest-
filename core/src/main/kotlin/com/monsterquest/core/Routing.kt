package com.monsterquest.core

import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.floor

/**
 * Walking routes on the game's own map: a shortest path that stays on the
 * streets read out of the map tiles. Lines that share a point (to about a
 * meter) are joined, so the network follows real intersections. The route
 * starts and ends on the street itself, at the spot closest to you and to the
 * destination, so it never cuts across buildings, yards or parks.
 */
object Routing {
    /** How far the start or the destination may be from the nearest street to snap onto it. */
    const val SNAP_M = 150.0
    /** Within this of the destination, you've arrived. */
    const val ARRIVED_M = 25.0
    /** Walking pace for the time estimate: about 1.3 m/s. */
    const val METERS_PER_MIN = 80.0

    class Graph internal constructor(
        val nodes: List<Pair<Double, Double>>,
        internal val edges: List<MutableList<Pair<Int, Double>>>,
        /** Each street piece once, as its two end nodes. */
        internal val segments: List<Pair<Int, Int>>,
    )

    fun build(lines: List<List<Pair<Double, Double>>>): Graph {
        val index = HashMap<Long, Int>()
        val nodes = ArrayList<Pair<Double, Double>>()
        val edges = ArrayList<MutableList<Pair<Int, Double>>>()
        val segments = ArrayList<Pair<Int, Int>>()
        fun node(p: Pair<Double, Double>): Int {
            val key = (floor(p.first * 1e5).toLong() shl 32) xor (floor(p.second * 1e5).toLong() and 0xffffffffL)
            return index.getOrPut(key) { nodes.add(p); edges.add(ArrayList()); nodes.size - 1 }
        }
        for (line in lines) {
            for (i in 1 until line.size) {
                val a = node(line[i - 1]); val b = node(line[i])
                if (a == b) continue
                val d = World.distanceMeters(nodes[a].first, nodes[a].second, nodes[b].first, nodes[b].second)
                edges[a] += b to d
                edges[b] += a to d
                segments += a to b
            }
        }
        return Graph(nodes, edges, segments)
    }

    /** Where [p] meets the street: the segment, the point on it, and how far along it (0..1). */
    private class Snap(val a: Int, val b: Int, val point: Pair<Double, Double>, val t: Double)

    /** The closest point to [p] on any street, within [SNAP_M]. */
    private fun snap(g: Graph, p: Pair<Double, Double>): Snap? {
        // A flat local projection is plenty over a few hundred meters.
        val kx = 111_320.0 * cos(Math.toRadians(p.first))
        val ky = 110_540.0
        var best: Snap? = null
        var bestD = SNAP_M
        for ((a, b) in g.segments) {
            val (alat, alon) = g.nodes[a]; val (blat, blon) = g.nodes[b]
            val ax = (alon - p.second) * kx; val ay = (alat - p.first) * ky
            val bx = (blon - p.second) * kx; val by = (blat - p.first) * ky
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + dx * t; val py = ay + dy * t
            val d = kotlin.math.sqrt(px * px + py * py)
            if (d < bestD) {
                bestD = d
                best = Snap(a, b, (alat + (blat - alat) * t) to (alon + (blon - alon) * t), t)
            }
        }
        return best
    }

    /**
     * The shortest walk from [from] to [to] along the streets, starting and
     * ending on the street at the points closest to each; null when either is
     * too far from any street or the two aren't connected by the streets
     * that are loaded.
     */
    fun route(g: Graph, from: Pair<Double, Double>, to: Pair<Double, Double>): List<Pair<Double, Double>>? {
        val sa = snap(g, from) ?: return null
        val sb = snap(g, to) ?: return null
        // Both on the same piece of street: just walk along it.
        if ((sa.a == sb.a && sa.b == sb.b) || (sa.a == sb.b && sa.b == sb.a)) return listOf(sa.point, sb.point)
        // The two snapped points join the network as extra nodes, linked to their segment's ends.
        val s = g.nodes.size
        val t = s + 1
        val points = HashMap<Int, Pair<Double, Double>>().apply { put(s, sa.point); put(t, sb.point) }
        fun at(i: Int) = points[i] ?: g.nodes[i]
        fun dist(i: Int, j: Int): Double { val p = at(i); val q = at(j); return World.distanceMeters(p.first, p.second, q.first, q.second) }
        val extra = HashMap<Int, MutableList<Pair<Int, Double>>>()
        fun link(i: Int, j: Int) {
            val d = dist(i, j)
            extra.getOrPut(i) { ArrayList() } += j to d
            extra.getOrPut(j) { ArrayList() } += i to d
        }
        link(s, sa.a); link(s, sa.b)
        link(t, sb.a); link(t, sb.b)
        fun next(u: Int): List<Pair<Int, Double>> = (if (u < g.edges.size) g.edges[u] else emptyList<Pair<Int, Double>>()) + extra[u].orEmpty()

        val best = HashMap<Int, Double>().apply { put(s, 0.0) }
        val prev = HashMap<Int, Int>()
        val open = PriorityQueue<Pair<Double, Int>>(compareBy { it.first }).apply { add(dist(s, t) to s) }
        val done = HashSet<Int>()
        while (open.isNotEmpty()) {
            val (_, u) = open.poll()
            if (!done.add(u)) continue
            if (u == t) break
            val du = best.getValue(u)
            for ((v, w) in next(u)) {
                val nd = du + w
                if (nd < (best[v] ?: Double.MAX_VALUE)) {
                    best[v] = nd
                    prev[v] = u
                    open += (nd + dist(v, t)) to v
                }
            }
        }
        if (t !in done) return null
        val path = ArrayList<Pair<Double, Double>>()
        var c: Int? = t
        while (c != null) { path += at(c); c = prev[c] }
        path.reverse()
        return path
    }

    fun length(path: List<Pair<Double, Double>>): Double =
        (1 until path.size).sumOf { World.distanceMeters(path[it - 1].first, path[it - 1].second, path[it].first, path[it].second) }

    /** Minutes to walk [meters], at least 1. */
    fun minutes(meters: Double): Int = maxOf(1, (meters / METERS_PER_MIN).toInt())
}
