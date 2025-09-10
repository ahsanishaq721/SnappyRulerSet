package com.snappyrulerset.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snappyrulerset.model.*
import com.snappyrulerset.snapping.*
import java.util.Locale
import kotlin.math.*

// Modes: Hand = pan; Line = free lines (snap to common angles); RulerLine = along ruler edge
// SquareLine = along nearest set-square edge; Tool = drag/rotate active tool
private enum class Mode { Hand, Freehand, Line, RulerLine, SquareLine, Protractor, Compass, Tool }

// Active tool choice
private enum class ToolKind { Ruler, SetSquare45, SetSquare30_60, Protractor, Compass }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(vm: com.snappyrulerset.viewmodel.DrawingViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val dpi = LocalContext.current.resources.displayMetrics.densityDpi.toFloat()
    val gridMm = 5f // 5mm grid spacing
    val mmPerPx = 25.4f / dpi // accurate mm to pixel conversion
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Mode / Tool
    var mode by remember { mutableStateOf(Mode.Hand) }
    var activeTool by remember { mutableStateOf(ToolKind.SetSquare45) } // default to triangle to see it immediately

    // Tool transform (shared: ruler or set square)
    var toolPos by remember { mutableStateOf(Vec2(0f, 0f)) }
    var toolAngleRad by remember { mutableStateOf(0f) }
    var compassRadius by remember { mutableStateOf(60f) }
    var protractorBaseAngle by remember { mutableStateOf<Float?>(null) }

    // Drag session
    var dragging by remember { mutableStateOf(false) }
    var startW by remember { mutableStateOf(Vec2(0f, 0f)) }
    var endW by remember { mutableStateOf<Vec2?>(null) }
    var hud by remember { mutableStateOf("") }
    var lastSnap by remember { mutableStateOf("") }
    var highlight by remember { mutableStateOf<Vec2?>(null) }
    var snapOff by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(0, 0)) }

    // Permission launcher for Android 13+
    val ctx = LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val width = if (canvasSize.width > 0) canvasSize.width else 1080
            val height = if (canvasSize.height > 0) canvasSize.height else 1920
            saveToGallery(ctx, state, width, height)
        } else {
            android.widget.Toast.makeText(ctx, "Permission needed to save to Gallery", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Center newly selected Protractor in the current viewport if not placed yet
    LaunchedEffect(activeTool, canvasSize, state.viewport.pan, state.viewport.zoom) {
        if (activeTool == ToolKind.Protractor && toolPos.x == 0f && toolPos.y == 0f && canvasSize.width > 0 && canvasSize.height > 0) {
            val cx = state.viewport.pan.x + (canvasSize.width / state.viewport.zoom) / 2f
            val cy = state.viewport.pan.y + (canvasSize.height / state.viewport.zoom) / 2f
            toolPos = Vec2(cx, cy)
        }
    }

    // If never positioned, auto-center any tool once when canvas is known
    LaunchedEffect(canvasSize) {
        if (toolPos.x == 0f && toolPos.y == 0f && canvasSize.width > 0 && canvasSize.height > 0) {
            val cx = state.viewport.pan.x + (canvasSize.width / state.viewport.zoom) / 2f
            val cy = state.viewport.pan.y + (canvasSize.height / state.viewport.zoom) / 2f
            toolPos = Vec2(cx, cy)
        }
    }

    // Points index (endpoints + midpoints + intersections + circle centers)
    val lines = remember(state.shapes) { state.shapes.filterIsInstance<Shape.Line>() }
    val circles = remember(state.shapes) { state.shapes.filterIsInstance<Shape.Circle>() }
    val pointIndex = remember(lines, circles) {
        // Finer cell size improves endpoint search accuracy
        SpatialIndex(32f).apply {
            val pts = collectLinePoints(lines) + collectIntersections(lines) + collectCircleCenters(circles)
            insertAll(pts)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Snappy Ruler Set") })
        },
        bottomBar = {
            BottomAppBar(actions = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f, false).horizontalScroll(rememberScrollState())) {
                        // Modes (scrollable)
                        FilterChip(onClick = { mode = Mode.Hand }, selected = mode == Mode.Hand, label = { Text("Hand") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(onClick = { mode = Mode.Freehand }, selected = mode == Mode.Freehand, label = { Text("Freehand") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(onClick = { mode = Mode.Line }, selected = mode == Mode.Line, label = { Text("Line") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(onClick = { mode = Mode.RulerLine }, selected = mode == Mode.RulerLine, label = { Text("RulerLine") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(onClick = { mode = Mode.SquareLine }, selected = mode == Mode.SquareLine, label = { Text("SquareLine") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(onClick = { mode = Mode.Protractor }, selected = mode == Mode.Protractor, label = { Text("Protractor") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(onClick = { mode = Mode.Compass }, selected = mode == Mode.Compass, label = { Text("Compass") })
                    }
                    Spacer(Modifier.width(8.dp))
                    Row {
                        IconButton(onClick = { vm.undo() }) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
                        IconButton(onClick = { vm.redo() }) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo") }
                    }
                }
            }, floatingActionButton = {
                FloatingActionButton(onClick = {
                    val width = if (canvasSize.width > 0) canvasSize.width else 1080
                    val height = if (canvasSize.height > 0) canvasSize.height else 1920
                    
                    // Check and request permissions for Android 13+
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val permission = android.Manifest.permission.READ_MEDIA_IMAGES
                        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(permission)
                            return@FloatingActionButton
                        }
                    }
                    
                    saveToGallery(ctx, state, width, height)
                }) { Icon(Icons.Filled.Save, contentDescription = "Save") }
            })
        }
    ) { pad ->
        Box(Modifier
            .fillMaxSize()
            .padding(pad)) {
            // Tools row under title (scrollable)
            Row(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .padding(top = 56.dp, start = 12.dp, end = 12.dp)
                    .horizontalScroll(rememberScrollState())
                    .background(Color(0xCCFFFFFF), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                FilterChip(onClick = { activeTool = ToolKind.Ruler; mode = Mode.Tool }, selected = activeTool == ToolKind.Ruler && mode == Mode.Tool, label = { Text("Ruler") })
                Spacer(Modifier.width(6.dp))
                FilterChip(onClick = { activeTool = ToolKind.SetSquare45; mode = Mode.Tool }, selected = activeTool == ToolKind.SetSquare45 && mode == Mode.Tool, label = { Text("SetSq 45°") })
                Spacer(Modifier.width(6.dp))
                FilterChip(onClick = { activeTool = ToolKind.SetSquare30_60; mode = Mode.Tool }, selected = activeTool == ToolKind.SetSquare30_60 && mode == Mode.Tool, label = { Text("SetSq 30/60°") })
                Spacer(Modifier.width(6.dp))
                FilterChip(onClick = { activeTool = ToolKind.Protractor; mode = Mode.Tool }, selected = activeTool == ToolKind.Protractor && mode == Mode.Tool, label = { Text("Protractor") })
                Spacer(Modifier.width(6.dp))
                FilterChip(onClick = { activeTool = ToolKind.Compass; mode = Mode.Tool }, selected = activeTool == ToolKind.Compass && mode == Mode.Tool, label = { Text("Compass") })
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8F8F8))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(
                        state.viewport,
                        state.snapping,
                        mode,
                        activeTool,
                        pointIndex,
                        toolPos,
                        toolAngleRad
                    ) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var transformMode = false

                            if (mode == Mode.Line || mode == Mode.RulerLine || mode == Mode.SquareLine || mode == Mode.Protractor || mode == Mode.Compass || mode == Mode.Freehand) {
                                // Long-press = snap temporarily off
                                snapOff = withTimeoutOrNull(350) {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        if (e.changes.any { it.positionChanged() }) break
                                        if (e.changes.all { !it.pressed }) break
                                    }
                                } == null
                            }

                            do {
                                val event = awaitPointerEvent()
                                val changes = event.changes
                                val fingers = changes.count { it.pressed }
                                if (fingers >= 2) transformMode = true

                                if (transformMode) {
                                    val panDelta = event.calculatePan()
                                    val zoomDelta = event.calculateZoom()
                                    val rotDelta = event.calculateRotation()

                                    if (mode == Mode.Tool) {
                                        // Move/rotate ACTIVE TOOL (ruler or set-square)
                                        toolPos += Vec2(
                                            panDelta.x,
                                            panDelta.y
                                        ) * (1f / state.viewport.zoom)
                                        var a = toolAngleRad + rotDelta
                                        val snap = snapAngleIfClose(a, 4f) // snap to 0/30/45/60/90
                                        if (snap.snapped) {
                                            if (lastSnap != "toolAngle") {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                lastSnap = "toolAngle"
                                            }
                                            a = snap.value
                                        }
                                        toolAngleRad = a
                                        changes.forEach { it.consume() }
                                    } else {
                                        // Pan/zoom canvas in other modes
                                        vm.update { s ->
                                            s.copy(
                                                viewport = s.viewport.copy(
                                                    pan = s.viewport.pan + Vec2(
                                                        panDelta.x,
                                                        panDelta.y
                                                    ) * (1f / s.viewport.zoom),
                                                    zoom = (s.viewport.zoom * zoomDelta).coerceIn(
                                                        0.25f,
                                                        6f
                                                    )
                                                )
                                            )
                                        }
                                        changes.forEach { it.consume() }
                                        if (dragging) {
                                            dragging = false; endW = null
                                        }
                                    }
                                } else {
                                    when (mode) {
                                        Mode.Hand -> {
                                            val panDelta = event.calculatePan()
                                            vm.update { s ->
                                                s.copy(
                                                    viewport = s.viewport.copy(
                                                        pan = s.viewport.pan + Vec2(
                                                            panDelta.x,
                                                            panDelta.y
                                                        ) * (1f / s.viewport.zoom)
                                                    )
                                                )
                                            }
                                            changes.forEach { it.consume() }
                                        }

                                        Mode.Line, Mode.RulerLine, Mode.SquareLine, Mode.Protractor, Mode.Compass, Mode.Freehand -> {
                                            val pos = changes.firstOrNull()?.position ?: continue
                                            val world = screenToWorld(pos, state.viewport)

                                            if (!dragging) {
                                                startW = world
                                                endW = null
                                                dragging = true
                                                if (mode == Mode.Protractor) {
                                                    // Reset protractor for new measurement
                                                    protractorBaseAngle = null
                                                    hud = "Click to set first ray"
                                                }
                                                if (mode == Mode.Freehand) {
                                                    vm.update { s -> s.copy(shapes = s.shapes + Shape.Path(points = listOf(world))) }
                                                }
                                            } else {
                                                // pick direction
                                                val dir = when (mode) {
                                                    Mode.Line -> {
                                                        val raw = atan2(
                                                            (world - startW).y,
                                                            (world - startW).x
                                                        )
                                                        val ang =
                                                            if (state.snapping && !snapOff) nearestCommonAngle(
                                                                raw
                                                            ) else raw
                                                        fromAngle(ang)
                                                    }

                                                    Mode.RulerLine -> fromAngle(toolAngleRad)
                                                    Mode.SquareLine -> {
                                                        val dirs = triangleEdgeDirs(
                                                            activeTool,
                                                            toolPos,
                                                            toolAngleRad,
                                                            220f
                                                        )
                                                        val v = norm(world - startW)
                                                        dirs.maxBy { abs(dot(v, it)) }
                                                    }

                                                    Mode.Protractor -> fromAngle(0f)
                                                    Mode.Compass -> fromAngle(0f)
                                                    Mode.Freehand -> fromAngle(0f)

                                                    else -> fromAngle(0f)
                                                }

                                                var projected = when (mode) {
                                                    Mode.Compass, Mode.Freehand -> world
                                                    else -> projectOntoLine(startW, world, dir)
                                                }
                                                highlight = null

                                                // Enhanced snapping with priority system
                                                if (state.snapping && !snapOff) {
                                                    val radiusPx = dynamicSnapRadiusPx(state.viewport.zoom, basePx = 24f)
                                                    val snapCandidates = mutableListOf<SnapCandidate>()
                                                    
                                                    // 1. Point snaps (highest priority) - more generous for endpoints
                                                    val near = pointIndex.queryNear(projected)
                                                    near.forEach { point ->
                                                        val d = distance(point, projected) * state.viewport.zoom
                                                        val effectiveRadius = if (lastSnap == "point") radiusPx * 1.5f else radiusPx
                                                        if (d <= effectiveRadius) {
                                                            snapCandidates.add(SnapCandidate(point, 1, d, "point"))
                                                        }
                                                    }
                                                    
                                                    // 2. Segment snaps (medium priority)
                                                    if (mode != Mode.Compass) {
                                                        lines.forEach { seg ->
                                                            val cp = closestPointOnSegment(projected, seg.a, seg.b)
                                                            val d = distance(projected, cp) * state.viewport.zoom
                                                            if (d <= radiusPx) {
                                                                snapCandidates.add(SnapCandidate(cp, 2, d, "segment"))
                                                            }
                                                        }
                                                    }
                                                    
                                                    // 3. Grid snaps (lowest priority)
                                                    val gridP = snapToGrid(projected, gridMm, dpi)
                                                    val gridD = distance(gridP, projected) * state.viewport.zoom
                                                    if (gridD <= radiusPx) {
                                                        snapCandidates.add(SnapCandidate(gridP, 3, gridD, "grid"))
                                                    }
                                                    
                                                    // Pick best candidate (closest with highest priority)
                                                    val best = snapCandidates.minByOrNull { it.priority * 1000f + it.distancePx }
                                                    if (best != null) {
                                                        projected = best.pos
                                                        highlight = best.pos
                                                        if (lastSnap != best.label) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            lastSnap = best.label
                                                        }
                                                    }
                                                }

                                                when (mode) {
                                                    Mode.Protractor -> {
                                                        // First click sets base angle, second click measures from base
                                                        if (protractorBaseAngle == null) {
                                                            protractorBaseAngle = atan2((projected - toolPos).y, (projected - toolPos).x)
                                                            hud = "Set first ray"
                                                        } else {
                                                            val base = protractorBaseAngle!!
                                                            val cur = atan2((projected - toolPos).y, (projected - toolPos).x)
                                                            var delta = degrees(cur - base)
                                                            while (delta < 0f) delta += 360f
                                                            if (delta > 180f) delta = 360f - delta
                                                            
                                                            // snap readout to 0.5° accuracy and hard snap to common angles
                                                            val snappedHard = snapAngleIfClose(cur, 2f) // tighter threshold
                                                            val shown = if (snappedHard.snapped) degrees(snappedHard.value - base).absoluteValue else delta
                                                            if (snappedHard.snapped && lastSnap != "angleHard") {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastSnap = "angleHard"
                                                            }
                                                            // Round to nearest 0.5° for display
                                                            val rounded = (shown * 2f).roundToInt() / 2f
                                                            hud = String.format(Locale.US,"%.1f°", rounded)
                                                        }
                                                        endW = projected
                                                    }
                                                    Mode.Compass -> {
                                                        val r = distance(startW, projected)
                                                        compassRadius = r
                                                        val mm = r * mmPerPx
                                                        val cm = mm / 10f
                                                        hud = String.format(Locale.US, "r=%.1f cm (%.1f mm)", cm, mm)
                                                        endW = projected
                                                    }
                                                    Mode.Freehand -> {
                                                        endW = projected
                                                        vm.update { s ->
                                                            val last = s.shapes.lastOrNull()
                                                            if (last is Shape.Path) {
                                                                val newPts = last.points + projected
                                                                s.copy(shapes = s.shapes.dropLast(1) + last.copy(points = newPts))
                                                            } else s
                                                        }
                                                    }
                                                    else -> {
                                                        endW = projected
                                                        val mm = distance(startW, projected) * mmPerPx
                                                        val cm = mm / 10f
                                                        val deg = degrees(
                                                            atan2(
                                                                dir.y,
                                                                dir.x
                                                            )
                                                        ).let { if (it < 0) it + 360f else it }
                                                        hud = String.format(Locale.US,"%.1f cm (%.1f mm) | %.1f°", cm, mm, deg)
                                                    }
                                                }
                                            }
                                        }

                                        Mode.Tool -> { /* single-finger noop while editing tool */
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if ((mode == Mode.Line || mode == Mode.RulerLine || mode == Mode.SquareLine || mode == Mode.Compass || mode == Mode.Protractor || mode == Mode.Freehand) && !transformMode && dragging) {
                                dragging = false
                                when (mode) {
                                    Mode.Compass -> {
                                        endW?.let { e ->
                                            val r = distance(startW, e)
                                            vm.update { s -> s.copy(shapes = s.shapes + Shape.Circle(startW, r)) }
                                        }
                                    }
                                    Mode.Protractor -> {
                                        // measurement only, no shape
                                    }
                                    Mode.Freehand -> {
                                        // already added during drag
                                    }
                                    else -> {
                                        endW?.let { e ->
                                            vm.update { s ->
                                                s.copy(
                                                    shapes = s.shapes + Shape.Line(
                                                        startW,
                                                        e
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                endW = null
                            }
                            snapOff = false
                        }
                    }
            ) {
                // world->screen
                fun w2s(p: Vec2): Offset {
                    val z = state.viewport.zoom
                    val pan = state.viewport.pan
                    return Offset((p.x - pan.x) * z, (p.y - pan.y) * z)
                }

                // Grid - optimized for performance
                val stepWorld = mmToPx(gridMm, dpi)
                val step = stepWorld * state.viewport.zoom
                if (step > 8f && step < 200f) { // Only draw grid when visible and not too dense
                    val w = size.width
                    val h = size.height
                    val startX = -((state.viewport.pan.x * state.viewport.zoom) % step)
                    val startY = -((state.viewport.pan.y * state.viewport.zoom) % step)
                    
                    // Limit grid lines for performance
                    val maxLines = 50
                    val xCount = minOf(maxLines, (w / step).toInt() + 2)
                    val yCount = minOf(maxLines, (h / step).toInt() + 2)
                    
                    for (i in 0 until xCount) {
                        val x = startX + i * step
                        if (x >= 0 && x <= w) {
                            drawLine(Color(0xFFE0E0E0), Offset(x, 0f), Offset(x, h), 1f)
                        }
                    }
                    for (i in 0 until yCount) {
                        val y = startY + i * step
                        if (y >= 0 && y <= h) {
                            drawLine(Color(0xFFE0E0E0), Offset(0f, y), Offset(w, y), 1f)
                        }
                    }
                }

                // Existing shapes
                state.shapes.forEach { s ->
                    when (s) {
                        is Shape.Line -> drawLine(Color.Black, w2s(s.a), w2s(s.b), 3f)
                        is Shape.Circle -> {
                            val center = w2s(s.center)
                            val radius = s.r * state.viewport.zoom
                            drawCircle(Color.Black, radius, center, style = Stroke(width = 3f))
                        }
                        is Shape.Path -> {
                            val pts = s.points.map { w2s(it) }
                            for (i in 1 until pts.size) {
                                drawLine(Color.Black, pts[i-1], pts[i], 3f)
                            }
                        }
                        else -> {}
                    }
                }

                // --- Tool overlays ---
                when (activeTool) {
                    ToolKind.Ruler -> if (mode == Mode.Tool || mode == Mode.RulerLine) {
                        // Ruler: long blue line + body
                        val z = state.viewport.zoom
                        val center = w2s(toolPos)
                        val dir = fromAngle(toolAngleRad)
                        val ortho = Vec2(-dir.y, dir.x)
                        val halfLen = 1000f * z
                        val halfThick = 12f
                        val a = center + Offset(dir.x * halfLen, dir.y * halfLen)
                        val b = center - Offset(dir.x * halfLen, dir.y * halfLen)
                        drawLine(Color(0xFF3949AB), a, b, 3f)
                        val a1 = a + Offset(ortho.x * halfThick, ortho.y * halfThick)
                        val b1 = b + Offset(ortho.x * halfThick, ortho.y * halfThick)
                        val a2 = a - Offset(ortho.x * halfThick, ortho.y * halfThick)
                        val b2 = b - Offset(ortho.x * halfThick, ortho.y * halfThick)
                        drawLine(Color(0x303949AB), a1, b1, 8f)
                        drawLine(Color(0x303949AB), a2, b2, 8f)
                    }

                    ToolKind.SetSquare45, ToolKind.SetSquare30_60 -> if (mode == Mode.Tool || mode == Mode.SquareLine) {
                        // Triangle outline (edges used for SquareLine)
                        val c = toolPos
                        val size = 220f // world units width (tweak)
                        val pts = triangleWorldPoints(activeTool, c, toolAngleRad, size)
                        fun toOff(v: Vec2) = w2s(v)
                        drawLine(Color(0xFF00695C), toOff(pts[0]), toOff(pts[1]), 3f)
                        drawLine(Color(0xFF00695C), toOff(pts[1]), toOff(pts[2]), 3f)
                        drawLine(Color(0xFF00695C), toOff(pts[2]), toOff(pts[0]), 3f)
                    }

                    ToolKind.Protractor -> if (mode == Mode.Tool || mode == Mode.Protractor) {
                        val center = w2s(toolPos)
                        val r = 140f * state.viewport.zoom
                        // base line showing zero angle
                        val dir = fromAngle(toolAngleRad)
                        drawCircle(Color(0x803498DB), r, center, style = Stroke(width = 2f))
                        drawLine(Color(0xFF1E88E5), center, center + Offset(dir.x * r, dir.y * r), 3f)
                        // small tick when snapping angle (visual hint)
                        if (lastSnap == "angleHard" && endW != null) {
                            val p = w2s(endW!!)
                            drawCircle(Color(0xFF1E88E5), 5f, p)
                        }
                    }

                    ToolKind.Compass -> if (mode == Mode.Tool || mode == Mode.Compass) {
                        val center = w2s(toolPos)
                        val r = compassRadius * state.viewport.zoom
                        drawCircle(Color(0x80388E3C), r, center, style = Stroke(width = 2f))
                        // draw leg towards current pointer if dragging
                        if (mode == Mode.Compass && endW != null) {
                            drawLine(Color(0xFF388E3C), center, w2s(endW!!), 3f)
                        }
                    }
                }

                // Temp line
                if (dragging && endW != null && (mode == Mode.Line || mode == Mode.RulerLine || mode == Mode.SquareLine)) {
                    drawLine(Color(0xFF1565C0), w2s(startW), w2s(endW!!), 4f)
                }
                if (dragging && endW != null && mode == Mode.Protractor) {
                    // show two rays from toolPos: base and current
                    val r = 180f * state.viewport.zoom
                    val a = w2s(toolPos)
                    
                    // Draw base ray (first click)
                    if (protractorBaseAngle != null) {
                        val b1 = a + Offset(cos(protractorBaseAngle!!) * r, sin(protractorBaseAngle!!) * r)
                        drawLine(Color(0xFF1E88E5), a, b1, 4f)
                    }
                    
                    // Draw current ray (second click)
                    val currentAngle = atan2((endW!! - toolPos).y, (endW!! - toolPos).x)
                    val b2 = a + Offset(cos(currentAngle) * r, sin(currentAngle) * r)
                    drawLine(Color(0xFF1E88E5), a, b2, 4f)
                }
                if (dragging && endW != null && mode == Mode.Compass) {
                    // preview circle
                    val center = w2s(startW)
                    val radius = distance(startW, endW!!) * state.viewport.zoom
                    drawCircle(Color(0xFF388E3C), radius, center, style = Stroke(width = 2f))
                    // draw radius line
                    drawLine(Color(0xFF388E3C), center, w2s(endW!!), 3f)
                }

                // Snap highlight with visual affordance - larger and more attractive
                highlight?.let { hp ->
                    val center = w2s(hp)
                    val baseRadius = 12f
                    
                    // Different colors for different snap types
                    val color = when (lastSnap) {
                        "point" -> Color(0xFF2E7D32) // Green for points
                        "segment" -> Color(0xFF1976D2) // Blue for segments  
                        "grid" -> Color(0xFF757575) // Gray for grid
                        else -> Color(0xFF2E7D32)
                    }
                    
                    // Outer glow ring
                    drawCircle(color.copy(alpha = 0.3f), baseRadius + 6f, center, style = Stroke(width = 3f))
                    // Main ring
                    drawCircle(color, baseRadius + 2f, center, style = Stroke(width = 4f))
                    // Inner circle
                    drawCircle(color, baseRadius, center, style = Stroke(width = 2f))
                    // Center dot
                    drawCircle(color, 5f, center)
                }
            }

            if (hud.isNotEmpty() && (mode == Mode.Line || mode == Mode.RulerLine || mode == Mode.SquareLine || mode == Mode.Protractor || mode == Mode.Compass)) {
                Column(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color(0xAA000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        hud,
                        color = Color.White
                    )
                    if (mode == Mode.Line || mode == Mode.RulerLine || mode == Mode.SquareLine || mode == Mode.Compass) {
                        Text(
                            "Calibrated for ${dpi.toInt()}dpi (${String.format("%.2f", mmPerPx)}mm/px)",
                            color = Color(0xFFCCCCCC),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/* ---------------- helpers ---------------- */

private fun saveToGallery(ctx: android.content.Context, state: com.snappyrulerset.model.DrawingState, width: Int, height: Int) {
    try {
        // Save to gallery using MediaStore (Android 10+)
        val contentResolver = ctx.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "snappy_ruler_${System.currentTimeMillis()}.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/SnappyRulerSet")
        }
        
        val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { imageUri ->
            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                val bitmap = com.snappyrulerset.export.Exporter.createBitmap(state, state.viewport, width, height)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                android.widget.Toast.makeText(ctx, "Saved to Gallery", android.widget.Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            // Fallback to cache directory if MediaStore fails
            val file = java.io.File(ctx.cacheDir, "snappy_export.png")
            com.snappyrulerset.export.Exporter.exportPng(state, state.viewport, width, height, file)
            android.widget.Toast.makeText(ctx, "Saved to ${file.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        // Fallback to cache directory if any error occurs
        val file = java.io.File(ctx.cacheDir, "snappy_export.png")
        com.snappyrulerset.export.Exporter.exportPng(state, state.viewport, width, height, file)
        android.widget.Toast.makeText(ctx, "Saved to ${file.absolutePath}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun screenToWorld(p: Offset, vp: Viewport) =
    Vec2(vp.pan.x + p.x / vp.zoom, vp.pan.y + p.y / vp.zoom)

private fun fromAngle(theta: Float) = Vec2(cos(theta), sin(theta))

// Project onto infinite line through 'origin' with direction 'dir'
private fun projectOntoLine(origin: Vec2, p: Vec2, dir: Vec2): Vec2 {
    val v = p - origin
    val t = dot(v, dir)
    return origin + dir * t
}

private data class SnapResult(val value: Float, val snapped: Boolean)

private val COMMON_ANGLES = floatArrayOf(0f, 30f, 45f, 60f, 90f, 120f, 135f, 150f, 180f)

private fun nearestCommonAngle(raw: Float): Float {
    val twoPI = (Math.PI * 2).toFloat()
    fun norm(a: Float): Float {
        var x = (a % twoPI + twoPI) % twoPI
        if (x > Math.PI) x -= twoPI
        return x
    }

    val bestDeg = COMMON_ANGLES.minBy { abs(norm(raw - radians(it))) }
    return radians(bestDeg)
}

private fun snapAngleIfClose(rawRad: Float, thresholdDeg: Float): SnapResult {
    val bestDeg = COMMON_ANGLES.minBy { abs(degrees(rawRad) - it).let { d -> min(d, 360f - d) } }
    val bestRad = radians(bestDeg)
    val delta = abs(degrees(rawRad - bestRad)).let { d -> min(d, 360f - d) }
    return if (delta <= thresholdDeg) SnapResult(bestRad, true) else SnapResult(rawRad, false)
}

private fun mmToPx(mm: Float, dpi: Float) = (dpi / 25.4f) * mm
private fun dynamicSnapRadiusPx(zoom: Float, basePx: Float = 16f) =
    (basePx / zoom).coerceIn(6f, 28f)

private fun snapToGrid(p: Vec2, gridMm: Float, dpi: Float): Vec2 {
    val s = mmToPx(gridMm, dpi)
    fun r(v: Float) = round(v / s) * s
    return Vec2(r(p.x), r(p.y))
}

// ---- Set Square geometry ----
private fun triangleEdgeDirs(
    kind: ToolKind,
    center: Vec2,
    baseAngle: Float,
    size: Float
): List<Vec2> {
    val pts = triangleWorldPoints(kind, center, baseAngle, size)
    if (pts.size < 3) return listOf(fromAngle(baseAngle)) // fallback

    return listOf(
        norm(pts[1] - pts[0]),
        norm(pts[2] - pts[1]),
        norm(pts[0] - pts[2])
    )
}

private fun triangleWorldPoints(
    kind: ToolKind,
    center: Vec2,
    baseAngle: Float,
    size: Float
): List<Vec2> {
    // Build an axis-aligned triangle then rotate by baseAngle and translate to center
    val raw = when (kind) {
        ToolKind.SetSquare45 -> {
            val a = Vec2(-size / 2f, -size / 2f)
            val b = Vec2(size / 2f, -size / 2f)
            val c = Vec2(-size / 2f, size / 2f)
            listOf(a, b, c)
        }

        ToolKind.SetSquare30_60 -> {
            val short = size
            val long = size * sqrt(3f)
            val a = Vec2(-short / 2f, -long / 2f) // vertical long leg
            val b = Vec2(short / 2f, -long / 2f)
            val c = Vec2(-short / 2f, long / 2f)
            listOf(a, b, c)
        }

        else -> listOf(Vec2(0f, 0f), Vec2(0f, 0f), Vec2(0f, 0f))
    }
    val ca = cos(baseAngle);
    val sa = sin(baseAngle)
    fun rot(p: Vec2) = Vec2(p.x * ca - p.y * sa, p.x * sa + p.y * ca)
    return raw.map { center + rot(it) }
}
