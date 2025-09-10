package com.snappyrulerset.snapping

import com.snappyrulerset.model.*
import kotlin.math.abs

// Endpoints + midpoints from existing lines + circle centers
fun collectLinePoints(lines: List<Shape.Line>): List<Vec2> {
    val pts = ArrayList<Vec2>(lines.size * 3)
    lines.forEach { l ->
        pts.add(l.a)
        pts.add(l.b)
        pts.add(Vec2((l.a.x + l.b.x) / 2f, (l.a.y + l.b.y) / 2f))
    }
    return pts
}

// Circle centers for snapping
fun collectCircleCenters(circles: List<Shape.Circle>): List<Vec2> {
    return circles.map { it.center }
}

// Pairwise intersections (simple O(n^2); fine for small drawings)
fun collectIntersections(lines: List<Shape.Line>): List<Vec2> {
    fun ccw(a: Vec2, b: Vec2, c: Vec2) = (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
    fun segsIntersect(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Boolean =
        ccw(a, c, d) != ccw(b, c, d) && ccw(a, b, c) != ccw(a, b, d)

    fun intersection(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Vec2? {
        val x1 = a.x; val y1 = a.y; val x2 = b.x; val y2 = b.y
        val x3 = c.x; val y3 = c.y; val x4 = d.x; val y4 = d.y
        val denom = (x1-x2)*(y3-y4) - (y1-y2)*(x3-x4)
        if (abs(denom) < 1e-4f) return null
        val px = ((x1*y2 - y1*x2)*(x3-x4) - (x1-x2)*(x3*y4 - y3*x4)) / denom
        val py = ((x1*y2 - y1*x2)*(y3-y4) - (y1-y2)*(x3*y4 - y3*x4)) / denom
        return Vec2(px, py)
    }

    val out = ArrayList<Vec2>()
    for (i in 0 until lines.size) {
        for (j in i+1 until lines.size) {
            val l1 = lines[i]; val l2 = lines[j]
            if (segsIntersect(l1.a, l1.b, l2.a, l2.b)) {
                intersection(l1.a, l1.b, l2.a, l2.b)?.let { out.add(it) }
            }
        }
    }
    return out
}