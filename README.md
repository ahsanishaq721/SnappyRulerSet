# Snappy Ruler Set

A precision drawing app for Android featuring virtual geometry tools with intelligent snapping. Built with Jetpack Compose for 60fps performance on mid-range devices.

## Architecture Overview

**MVVM + Compose Pattern:**
- **UI Layer**: `DrawingScreen.kt` - Main Compose canvas with gesture handling, tool overlays, and HUD
- **ViewModel**: `DrawingViewModel.kt` - Manages `StateFlow<DrawingState>` with 50-step undo/redo
- **Model Layer**: 
  - `ToolState.kt` - Core state (shapes, viewport, snapping config)
  - `Shapes.kt` - Shape definitions (Line, Circle, Arc, Point, Path)
  - `Geometry.kt` - Math utilities (vectors, angles, projections)

**Rendering Loop:**
1. Grid rendering (capped for performance)
2. Existing shapes (lines, circles, paths)
3. Active tool overlays (ruler, set squares, protractor, compass)
4. Transient previews (while drawing)
5. Snap highlights with visual feedback
6. Precision HUD (measurements)

**State Flow:**
```
User Gesture → DrawingScreen → DrawingViewModel.update() → StateFlow → UI Recomposition
```

## Snapping Strategy & Data Structures

**Spatial Index:**
- `SpatialIndex.kt` - Uniform grid (32px cells) for O(1) proximity queries
- `SnapPoints.kt` - Collects endpoints, midpoints, intersections, circle centers
- `SnapEngine.kt` - Angle snapping logic and common angle detection

**Snap Priority System:**
1. **Points** (endpoints/midpoints/intersections) - Priority 1
2. **Segments** (closest point on existing lines) - Priority 2  
3. **Grid** (5mm spacing) - Priority 3

**Hit Testing:**
- Dynamic snap radius: 6-28px (scales with zoom)
- "Sticky" behavior: 1.5x radius once snapping to maintain lock
- Visual feedback: Multi-ring highlights (green=point, blue=segment, gray=grid)

## Performance Notes

**Target:** 60fps on 6GB RAM devices

**Optimizations:**
- Grid rendering limited to 50 lines max, only when visible (8-200px spacing)
- Spatial index with bounded neighbor scans (max 20 results)
- Shape processing capped (100 lines, 50 circles) for large drawings
- Minimal allocations in gesture loop

**Trade-offs:**
- Line intersections computed O(n²) - acceptable for small/medium drawings
- Snap radius increased for better UX vs. precision balance
- Tool overlays only render in relevant modes to reduce draw calls

## Calibration & Real-World Units

**DPI-Based Conversion:**
```kotlin
mmPerPx = 25.4f / deviceDpi
```

**HUD Display:**
- Shows lengths in cm and mm with 1mm granularity
- Displays calibration info: "Calibrated for 420dpi (0.06mm/px)"
- Protractor rounds to 0.5° with hard snaps at common angles

**Calibration Flow (if needed):**
1. Display 100mm test line on screen
2. User measures with physical ruler
3. Input measured value to adjust scale factor
4. App applies correction for precise physical accuracy

## Key Files

- `ui/DrawingScreen.kt` - Main canvas, tools, gestures, snapping
- `viewmodel/DrawingViewModel.kt` - State management, undo/redo
- `model/` - State classes and math utilities
- `snapping/` - Spatial index and snap point generation
- `export/Exporter.kt` - Bitmap rendering and MediaStore integration

## Offline-First

No network dependencies. All functionality works locally with gallery export via MediaStore API.
