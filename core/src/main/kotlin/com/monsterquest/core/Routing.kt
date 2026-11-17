package com.monsterquest.core

import java.util.PriorityQueue
import kotlin.math.floor

/**
 * Walking routes on the game's own map: a shortest path along the streets
 * and footpaths read out of the map tiles. Lines that share a point (to about
 * a meter) are joined, so the network follows real intersections.
 */
object Routing {
    /** How far the start or the destination may be from the nearest street to snap onto it. */
    const val SNAP_M = 150.0
    /** Within this of the destination, you've arrived. */
    const val ARRIVED_M = 25.0
    /** Walking pace for the time estimate: about 1.3 m/s. */
    const val METERS_PER_MIN = 80.0

    class Graph internal constructor(val nodes: List<Pair<Double, Double>>, internal val edges: List<MutableList<Pair<Int, Double>>>)

    fun build(lines: List<List<Pair<Double, Double>>>): Graph {
        val index = HashMap<Long, Int>()
        val nodes = ArrayList<Pair<Double, Double>>()
        val edges = ArrayList<MutableList<Pair<Int, Double>>>()
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
            }
        }
        return Graph(nodes, edges)
    }

    private fun nearest(g: Graph, p: Pair<Double, Double>): Int? {
        var best = -1
        var bestD = SNAP_M
        g.nodes.forEachIndexed { i, n ->
            val d = World.distanceMeters(p.first, p.second, n.first, n.second)
            if (d < bestD && g.edges[i].isNotEmpty()) { bestD = d; best = i }
        }
        return best.takeIf { it >= 0 }
    }

    /**
     * The shortest walk from [from] to [to] along the network, starting and
     * ending at the exact points; null when either is too far from any street
     * or the two aren't connected by the streets that are loaded.
     */
    fun route(g: Graph, from: Pair<Double, Double>, to: Pair<Double, Double>): List<Pair<Double, Double>>? {
        val s = nearest(g, from) ?: return null
        val t = nearest(g, to) ?: return null
        val dist = HashMap<Int, Double>().apply { put(s, 0.0) }
        val prev = HashMap<Int, Int>()
        val goal = g.nodes[t]
        fun h(i: Int) = World.distanceMeters(g.nodes[i].first, g.nodes[i].second, goal.first, goal.second)
        val open = PriorityQueue<Pair<Double, Int>>(compareBy { it.first }).apply { add(h(s) to s) }
        val done = HashSet<Int>()
        while (open.isNotEmpty()) {
            val (_, u) = open.poll()
            if (!done.add(u)) continue
            if (u == t) break
            val du = dist.getValue(u)
            for ((v, w) in g.edges[u]) {
                val nd = du + w
                if (nd < (dist[v] ?: Double.MAX_VALUE)) {
                    dist[v] = nd
                    prev[v] = u
                    open += (nd + h(v)) to v
                }
            }
        }
        if (t !in done) return null
        val path = ArrayList<Pair<Double, Double>>()
        var c: Int? = t
        while (c != null) { path += g.nodes[c]; c = prev[c] }
        path.reverse()
        return listOf(from) + path + listOf(to)
    }

    /** A straight line, when there's no street route. */
    fun straight(from: Pair<Double, Double>, to: Pair<Double, Double>) = listOf(from, to)

    fun length(path: List<Pair<Double, Double>>): Double =
        (1 until path.size).sumOf { World.distanceMeters(path[it - 1].first, path[it - 1].second, path[it].first, path[it].second) }

    /** Minutes to walk [meters], at least 1. */
    fun minutes(meters: Double): Int = maxOf(1, (meters / METERS_PER_MIN).toInt())
}
