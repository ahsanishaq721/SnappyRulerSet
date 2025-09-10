package com.snappyrulerset.snapping

import com.snappyrulerset.model.Vec2
import kotlin.math.floor

class SpatialIndex(private val cell: Float = 64f) {
    private val map = HashMap<Long, MutableList<Vec2>>()

    private fun key(x: Float, y: Float): Long {
        val cx = floor(x / cell).toLong()
        val cy = floor(y / cell).toLong()
        return (cx shl 32) xor (cy and 0xffffffffL)
    }

    fun clear() = map.clear()

    fun insert(p: Vec2) {
        map.getOrPut(key(p.x, p.y)) { mutableListOf() }.add(p)
    }

    fun insertAll(points: Iterable<Vec2>) { points.forEach { insert(it) } }

    fun queryNear(p: Vec2, maxResults: Int = 20): List<Vec2> {
        val cx = floor(p.x / cell).toLong()
        val cy = floor(p.y / cell).toLong()
        val out = ArrayList<Vec2>(maxResults)
        for (dy in -1..1) for (dx in -1..1) {
            if (out.size >= maxResults) break
            val k = ((cx + dx) shl 32) xor ((cy + dy) and 0xffffffffL)
            map[k]?.let { 
                for (point in it) {
                    if (out.size >= maxResults) break
                    out.add(point)
                }
            }
        }
        return out
    }
}