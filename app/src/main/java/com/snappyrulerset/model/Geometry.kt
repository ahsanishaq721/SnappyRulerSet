package com.snappyrulerset.model

import kotlin.math.*

data class Vec2(val x: Float, val y: Float)

// Operator overloads should be declared at top-level (outside the class)
// so they are globally available:
operator fun Vec2.plus(o: Vec2) = Vec2(x + o.x, y + o.y)
operator fun Vec2.minus(o: Vec2) = Vec2(x - o.x, y - o.y)
operator fun Vec2.times(s: Float) = Vec2(x * s, y * s)
operator fun Vec2.div(s: Float) = Vec2(x / s, y / s)

// Vector math helpers
fun dot(a: Vec2, b: Vec2) = a.x * b.x + a.y * b.y
fun len(v: Vec2) = hypot(v.x.toDouble(), v.y.toDouble()).toFloat()
fun norm(v: Vec2): Vec2 {
    val l = len(v)
    return if (l == 0f) v else v * (1f / l)
}
fun angleOf(v: Vec2): Float = atan2(v.y, v.x)
fun fromAngle(theta: Float): Vec2 = Vec2(cos(theta), sin(theta))

fun degrees(rad: Float) = rad * 180f / Math.PI.toFloat()
fun radians(deg: Float) = deg * Math.PI.toFloat() / 180f

fun distance(a: Vec2, b: Vec2) = len(a - b)

// Closest point on segment AB to point P (returns A or B if projection lies outside)
fun closestPointOnSegment(p: Vec2, a: Vec2, b: Vec2): Vec2 {
    val ab = b - a
    val abLen2 = dot(ab, ab)
    if (abLen2 == 0f) return a
    val t = ((p - a).let { dot(it, ab) } / abLen2).coerceIn(0f, 1f)
    return a + ab * t
}

fun distancePointToSegment(p: Vec2, a: Vec2, b: Vec2): Float {
    val c = closestPointOnSegment(p, a, b)
    return distance(p, c)
}