package com.snappyrulerset.model

sealed class Shape {
    data class Line(val a: Vec2, val b: Vec2): Shape()
    data class Circle(val center: Vec2, val r: Float): Shape()
    data class Arc(val center: Vec2, val r: Float, val startRad: Float, val sweepRad: Float): Shape()
    data class Point(val p: Vec2): Shape()
    data class Path(val points: List<Vec2>): Shape()
}