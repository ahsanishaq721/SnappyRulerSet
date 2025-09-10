package com.snappyrulerset.model

enum class ToolKind { Ruler, SetSquare45, SetSquare3060, Protractor, Compass }

data class ToolTransform(
    val position: Vec2 = Vec2(0f, 0f),
    val rotationRad: Float = 0f,
    val scale: Float = 1f
)


data class ToolState(
    val kind: ToolKind = ToolKind.Ruler,
    val transform: ToolTransform = ToolTransform()
)


data class Viewport(
    val pan: Vec2 = Vec2(0f, 0f),
    val zoom: Float = 1f
)


data class DrawingState(
    val shapes: List<Shape> = emptyList(),
    val tool: ToolState = ToolState(),
    val viewport: Viewport = Viewport(),
    val snapping: Boolean = true,
    val gridSpacingMm: Float = 5f, // Configurable grid spacing
    val snapRadiusPx: Float = 16f // Base snap radius
)