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

    /** A street line, and whether it's a bridge (1), a tunnel (-1) or on the ground (0). */
    data class Way(val points: List<Pair<Double, Double>>, val level: Int = 0)

    /** How close two streets must come to count as meeting (a T-junction drawn a little short, a line cut at a tile edge). */
    const val JOIN_M = 4.0

    fun build(lines: List<List<Pair<Double, Double>>>): Graph = buildWays(lines.map { Way(it) })

    /**
     * Joins the streets into one network. Map data often draws a street right
     * across another without a shared point, stops a side street just short of
     * the one it meets, or cuts a street at a tile edge: crossings, near
     * misses within [JOIN_M] and cut ends are all joined. A bridge or tunnel
     * only joins at its ends, never the streets it passes over or under.
     */
    fun buildWays(ways: List<Way>): Graph {
        val first = ways.firstOrNull { it.points.isNotEmpty() }?.points?.first() ?: return Graph(emptyList(), emptyList(), emptyList())
        val kx = 111_320.0 * cos(Math.toRadians(first.first))
        val ky = 110_540.0
        class Seg(val p: Pair<Double, Double>, val q: Pair<Double, Double>, val level: Int) {
            val px = p.second * kx; val py = p.first * ky; val qx = q.second * kx; val qy = q.first * ky
            /** Points along it where another street meets it: (0..1, point). */
            val splits = ArrayList<Pair<Double, Pair<Double, Double>>>()
            fun at(t: Double) = (p.first + (q.first - p.first) * t) to (p.second + (q.second - p.second) * t)
            /** How far along (0..1) the closest point to (x, y) is, and its distance in meters. */
            fun project(x: Double, y: Double): Pair<Double, Double> {
                val dx = qx - px; val dy = qy - py
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (((x - px) * dx + (y - py) * dy) / len2).coerceIn(0.0, 1.0)
                val cx = px + dx * t - x; val cy = py + dy * t - y
                return t to kotlin.math.sqrt(cx * cx + cy * cy)
            }
        }
        val segs = ArrayList<Seg>()
        for (w in ways) for (i in 1 until w.points.size) if (w.points[i - 1] != w.points[i]) segs += Seg(w.points[i - 1], w.points[i], w.level)
        // Only compare pieces of street that are near each other: a grid of ~60 m cells.
        val cell = 60.0
        val grid = HashMap<Long, MutableList<Int>>()
        segs.forEachIndexed { i, sg ->
            val x0 = floor((minOf(sg.px, sg.qx) - JOIN_M) / cell).toLong(); val x1 = floor((maxOf(sg.px, sg.qx) + JOIN_M) / cell).toLong()
            val y0 = floor((minOf(sg.py, sg.qy) - JOIN_M) / cell).toLong(); val y1 = floor((maxOf(sg.py, sg.qy) + JOIN_M) / cell).toLong()
            for (x in x0..x1) for (y in y0..y1) grid.getOrPut((x shl 32) xor (y and 0xffffffffL)) { ArrayList() } += i
        }
        val checked = HashSet<Long>()
        for (bucket in grid.values) for (ii in bucket.indices) for (jj in ii + 1 until bucket.size) {
            val i = minOf(bucket[ii], bucket[jj]); val j = maxOf(bucket[ii], bucket[jj])
            if (!checked.add(i.toLong() * segs.size + j)) continue
            val a = segs[i]; val b = segs[j]
            // Ends that touch the other street (T-junctions, tile cuts): joined at any level, as ramps meet roads.
            fun touch(from: Seg, endX: Double, endY: Double, end: Pair<Double, Double>, onto: Seg) {
                val (t, d) = onto.project(endX, endY)
                if (d <= JOIN_M) onto.splits += t to end
            }
            touch(a, a.px, a.py, a.p, b); touch(a, a.qx, a.qy, a.q, b)
            touch(b, b.px, b.py, b.p, a); touch(b, b.qx, b.qy, b.q, a)
            // A street drawn straight across another: a crossing, but not between a bridge or tunnel and the ground.
            if (a.level != b.level) continue
            val rx = a.qx - a.px; val ry = a.qy - a.py
            val sx = b.qx - b.px; val sy = b.qy - b.py
            val den = rx * sy - ry * sx
            if (kotlin.math.abs(den) < 1e-9) continue
            val wx = b.px - a.px; val wy = b.py - a.py
            val t = (wx * sy - wy * sx) / den
            val u = (wx * ry - wy * rx) / den
            if (t in 0.0..1.0 && u in 0.0..1.0) {
                val pt = a.at(t)
                a.splits += t to pt
                b.splits += u to pt
            }
        }
        val index = HashMap<Long, Int>()
        val nodes = ArrayList<Pair<Double, Double>>()
        val edges = ArrayList<MutableList<Pair<Int, Double>>>()
        val segments = ArrayList<Pair<Int, Int>>()
        fun node(p: Pair<Double, Double>): Int {
            val key = (floor(p.first * 1e5).toLong() shl 32) xor (floor(p.second * 1e5).toLong() and 0xffffffffL)
            return index.getOrPut(key) { nodes.add(p); edges.add(ArrayList()); nodes.size - 1 }
        }
        for (sg in segs) {
            val chain = (listOf(0.0 to sg.p) + sg.splits.sortedBy { it.first } + listOf(1.0 to sg.q)).map { node(it.second) }
            for (k in 1 until chain.size) {
                val a = chain[k - 1]; val b = chain[k]
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
