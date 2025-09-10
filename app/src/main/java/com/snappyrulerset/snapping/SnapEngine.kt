package com.snappyrulerset.snapping

import androidx.compose.runtime.Immutable
import com.snappyrulerset.model.Vec2
import com.snappyrulerset.model.distance
import com.snappyrulerset.model.minus
import com.snappyrulerset.model.radians
import kotlin.math.*


@Immutable
data class SnapCandidate(
    val pos: Vec2,
    val priority: Int,
    val distancePx: Float,
    val label: String
)


class SnapEngine {
    // Common angles in radians
    private val angleSet = floatArrayOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f)
        .map { radians(it) }.toFloatArray()


    fun mmToPx(mm: Float, dpi: Float): Float = (dpi / 25.4f) * mm


    fun dynamicSnapRadiusPx(zoom: Float, basePx: Float = 16f): Float =
        (basePx / zoom).coerceIn(6f, 28f)


    fun snapToGrid(current: Vec2, gridMm: Float, dpi: Float, zoom: Float): SnapCandidate {
        val gridPx = mmToPx(gridMm, dpi) * zoom // grid spacing in screen px for clarity
// We compute in world coords, so use world grid; spacing in world px = mmToPx(...)
        val spacing = mmToPx(gridMm, dpi)
        fun roundTo(v: Float, s: Float) = (kotlin.math.round(v / s) * s)
        val gx = roundTo(current.x, spacing)
        val gy = roundTo(current.y, spacing)
        val snapped = Vec2(gx, gy)
        val d = distance(current, snapped)
        return SnapCandidate(snapped, priority = 10, distancePx = d * zoom, label = "grid")
    }


    fun snapAngle(from: Vec2, to: Vec2): Pair<Float, Float> {
        val v = to - from
        val a = atan2(v.y, v.x)
        val best = angleSet.minBy { abs(angleDelta(a, it)) }
        return a to best
    }


    private fun angleDelta(a: Float, b: Float): Float {
        var d = abs(a - b) % (2f * Math.PI.toFloat())
        if (d > Math.PI) d = 2f * Math.PI.toFloat() - d
        return d
    }
}