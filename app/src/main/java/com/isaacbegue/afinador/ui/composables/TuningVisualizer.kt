package com.isaacbegue.afinador.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.isaacbegue.afinador.ui.theme.*
import kotlin.math.abs

// Constants
private const val HISTORY_DURATION_MS = 5000L
private const val CENTS_IN_TUNE_THRESHOLD = 10.0f
private const val VISUAL_CENTS_RANGE = 50.0f // Visual range +/- 50 cents

@Composable
fun TuningVisualizer(
    modifier: Modifier = Modifier,
    // History contains normalized offset vs Target (Fixed modes) or vs Detected (Canto mode)
    tuningHistory: List<Pair<Long, Float?>>,
    // Current offset vs Target (Fixed modes) or vs Detected (Canto mode) - Used for pin & color
    currentOffset: Float,
    isGraphCenteringDynamic: Boolean, // Currently used by VM logic, could be used here later if needed
    isNoteDetected: Boolean
) {
    val density = LocalDensity.current

    // Colors & Styles (unchanged)
    val lineGreen = TunerGreen
    val lineWhite = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val targetRangeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    val centerLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    val pinColor = TunerRed
    val thickStrokeDp: Dp = 3.dp; val thinStrokeDp: Dp = 1.5.dp; val centerLineStrokeDp: Dp = 1.dp
    val paddingDp: Dp = 10.dp; val pinSizeDp: Dp = 12.dp
    val thickLineStyle = remember(density, thickStrokeDp) { density.run { Stroke(width = thickStrokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round) } }
    val thinLineStyle = remember(density, thinStrokeDp) { density.run { Stroke(width = thinStrokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round) } }
    val centerLineStrokeStyle = remember(density, centerLineStrokeDp) { density.run { Stroke(width = centerLineStrokeDp.toPx(), cap = StrokeCap.Butt) } }
    val paddingX = remember(density, paddingDp) { density.run { paddingDp.toPx() } }
    val pinSize = remember(density, pinSizeDp) { density.run { pinSizeDp.toPx() } }


    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val now = System.currentTimeMillis()
        val drawingWidth = canvasWidth - 2 * paddingX
        val halfDrawingWidth = drawingWidth / 2f
        val visualCenterX = paddingX + halfDrawingWidth // Static visual center

        // --- 1. Draw Target Range & Center Line ---
        val targetRangeNormalized = (CENTS_IN_TUNE_THRESHOLD / VISUAL_CENTS_RANGE).coerceIn(0f, 1f)
        val targetRangePx = targetRangeNormalized * drawingWidth
        val backgroundCenterX = visualCenterX // Background is always visually centered now

        // Draw Target Range centered visually
        drawRect( color = targetRangeColor, topLeft = Offset(backgroundCenterX - targetRangePx, 0f), size = Size(targetRangePx * 2, canvasHeight) )
        // Draw Center Line visually centered
        drawLine( color = centerLineColor, start = Offset(backgroundCenterX, 0f), end = Offset(backgroundCenterX, canvasHeight), strokeWidth = centerLineStrokeStyle.width )

        // --- 2. Draw History Line ---
        if (tuningHistory.isNotEmpty()) {
            val isInTuneNow = abs(currentOffset) <= CENTS_IN_TUNE_THRESHOLD
            val currentLineStyle = if (isInTuneNow) thickLineStyle else thinLineStyle
            val currentLineColor = if (isInTuneNow) lineGreen else lineWhite

            val segments = mutableListOf<Path>()
            var currentPath: Path? = null

            // History now directly contains the appropriate normalized offset for the mode
            tuningHistory.forEach { (timestamp, normalizedOffsetNullable) ->
                val timeRatio = ((now - timestamp).toFloat() / HISTORY_DURATION_MS).coerceIn(0f, 1f)
                val y = timeRatio * canvasHeight

                if (normalizedOffsetNullable != null) {
                    val normalizedOffset = normalizedOffsetNullable.coerceIn(-1f, 1f)
                    // Calculate X position relative to the visual center using the offset from history
                    // This works for both modes because history value meaning is mode-dependent
                    val x = visualCenterX + normalizedOffset * halfDrawingWidth

                    if (currentPath == null) { currentPath = Path().apply { moveTo(x, y) } } else { currentPath?.lineTo(x, y) }
                } else { currentPath?.let { if (!it.isEmpty) segments.add(it) }; currentPath = null } // Gap
            }
            currentPath?.let { if (!it.isEmpty) segments.add(it) }

            segments.forEach { segmentPath -> drawPath( path = segmentPath, color = currentLineColor, style = currentLineStyle ) }
        }

        // --- 3. Draw Pin Indicator ---
        if (isNoteDetected) {
            // Pin position uses the *current* offset (vs Target or vs Detected based on mode)
            val normalizedCurrentOffset = (currentOffset / VISUAL_CENTS_RANGE).coerceIn(-1f, 1f)
            val pinX = visualCenterX + normalizedCurrentOffset * halfDrawingWidth

            val pinPath = Path().apply { moveTo(pinX, 0f); lineTo(pinX - pinSize / 2, pinSize); lineTo(pinX + pinSize / 2, pinSize); close() }
            drawPath(pinPath, color = pinColor)
        }
    }
}

// --- Previews (Keep previews from previous step) ---

@Preview(showBackground = true, name = "Fixed Center (Target=A4, Detected=A#4)")
@Composable
private fun TuningVisualizerPreview_Fixed_SharpDetected() {
    AfinadorTheme { Surface(Modifier.height(200.dp).fillMaxWidth()) { val history = remember { val now = System.currentTimeMillis(); val targetOffset = 95.0f; (0..10).map { i -> now - (i * 500L) to ((targetOffset - i*2) / VISUAL_CENTS_RANGE).coerceIn(-1f,1f) }.reversed() }; TuningVisualizer( tuningHistory = history, currentOffset = 95.0f, isGraphCenteringDynamic = false, isNoteDetected = true ) } }
}
@Preview(showBackground = true, name = "Fixed Center (Target=A4, Detected=G#4)")
@Composable
private fun TuningVisualizerPreview_Fixed_FlatDetected() {
    AfinadorTheme { Surface(Modifier.height(200.dp).fillMaxWidth()) { val history = remember { val now = System.currentTimeMillis(); val targetOffset = -95.0f; (0..10).map { i -> now - (i * 500L) to ((targetOffset + i*2) / VISUAL_CENTS_RANGE).coerceIn(-1f,1f) }.reversed() }; TuningVisualizer( tuningHistory = history, currentOffset = -95.0f, isGraphCenteringDynamic = false, isNoteDetected = true ) } }
}
@Preview(showBackground = true, name = "Fixed Center (Target=A4, Detected=A4)")
@Composable
private fun TuningVisualizerPreview_Fixed_Centered() {
    AfinadorTheme { Surface(Modifier.height(200.dp).fillMaxWidth()) { val history = remember { val now = System.currentTimeMillis(); val targetOffset = 5.0f; (0..10).map { i -> now - (i * 500L) to (targetOffset / VISUAL_CENTS_RANGE).coerceIn(-1f,1f) }.reversed() }; TuningVisualizer( tuningHistory = history, currentOffset = 5.0f, isGraphCenteringDynamic = false, isNoteDetected = true ) } }
}
@Preview(showBackground = true, name = "Dynamic Center (Canto, Detected=A4)")
@Composable
private fun TuningVisualizerPreview_Dynamic_Centered() {
    AfinadorTheme { Surface(Modifier.height(200.dp).fillMaxWidth()) { val history = remember { val now = System.currentTimeMillis(); (0..10).map { i -> now - (i * 500L) to (5f / VISUAL_CENTS_RANGE) }.reversed() }; TuningVisualizer( tuningHistory = history, currentOffset = 5.0f, isGraphCenteringDynamic = true, isNoteDetected = true ) } }
}
@Preview(showBackground = true, name = "Fixed Center, Gaps, No Detection")
@Composable
private fun TuningVisualizerPreview_Fixed_Gaps_NoDetect() {
    AfinadorTheme { Surface(Modifier.height(200.dp).fillMaxWidth()) { val history = remember { val now = System.currentTimeMillis(); listOf( now - 4800L to -25f / VISUAL_CENTS_RANGE, now - 4000L to 10f / VISUAL_CENTS_RANGE, now - 3500L to 15f / VISUAL_CENTS_RANGE, now - 3000L to null, now - 2800L to null, now - 2500L to -20f / VISUAL_CENTS_RANGE, now - 1500L to -5f / VISUAL_CENTS_RANGE, now - 1000L to null ) }; TuningVisualizer( tuningHistory = history, currentOffset = -5.0f, isGraphCenteringDynamic = false, isNoteDetected = false ) } }
}