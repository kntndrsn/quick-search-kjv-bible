package com.kntndrsn.quicksearchkjvbible.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun Modifier.pinchToZoom(
    onZoom: (Float) -> Unit
): Modifier {
    val zoomText by rememberUpdatedState(onZoom)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zooming = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom.isFinite() && zoom > 0f) {
                        zooming = true
                        if (zoom != 1f) zoomText(zoom)
                    }
                }
                if (zooming) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}
