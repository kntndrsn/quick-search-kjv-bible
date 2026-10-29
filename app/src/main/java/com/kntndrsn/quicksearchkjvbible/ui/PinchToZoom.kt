package com.kntndrsn.quicksearchkjvbible.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.kntndrsn.quicksearchkjvbible.MainViewModel

@Composable
internal fun Modifier.pinchToZoom(
    textScale: () -> Float,
    onZoomCommitted: (Float) -> Unit
): Modifier {
    var gestureScale by remember { mutableFloatStateOf(1f) }
    var gestureOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    val currentScale by rememberUpdatedState(textScale)
    val commitZoom by rememberUpdatedState(onZoomCommitted)
    return graphicsLayer {
        scaleX = gestureScale
        scaleY = gestureScale
        transformOrigin = gestureOrigin
    }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zooming = false
            var totalZoom = 1f
            val initialScale = currentScale()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        if (zoom.isFinite() && zoom > 0f) {
                            if (!zooming) {
                                val centroid = event.calculateCentroid()
                                gestureOrigin = TransformOrigin(
                                    (centroid.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                    (centroid.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                )
                            }
                            zooming = true
                            totalZoom = (totalZoom * zoom).coerceIn(
                                MainViewModel.MIN_TEXT_SCALE / initialScale,
                                MainViewModel.MAX_TEXT_SCALE / initialScale
                            )
                            gestureScale = totalZoom
                        }
                    }
                    if (zooming) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            } finally {
                gestureScale = 1f
                if (zooming && totalZoom != 1f) commitZoom(totalZoom)
            }
        }
    }
}
