package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * SharedDraggableTwoPaneLayout — KMP equivalent of Android's DraggableTwoPaneLayout.
 * Allows dragging the middle handle left and right to resize the Catalog list and Detail panes.
 */
@Composable
fun SharedDraggableTwoPaneLayout(
    firstPane: @Composable (Modifier) -> Unit,
    secondPane: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    defaultSplitFraction: Float = 0.45f,
    minSplitFraction: Float = 0.20f,
    maxSplitFraction: Float = 0.80f,
    dividerWidth: Dp = 10.dp
) {
    var splitFraction by remember { mutableStateOf(defaultSplitFraction) }
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val totalWidthDp = maxWidth

        val firstPaneWidth = ((totalWidthDp - dividerWidth) * splitFraction).coerceAtLeast(180.dp)
        val secondPaneWidth = (totalWidthDp - dividerWidth - firstPaneWidth).coerceAtLeast(240.dp)

        Row(modifier = Modifier.fillMaxSize()) {
            // Left Pane
            Box(
                modifier = Modifier
                    .width(firstPaneWidth)
                    .fillMaxHeight()
            ) {
                firstPane(Modifier.fillMaxSize())
            }

            // Draggable Middle Divider
            Box(
                modifier = Modifier
                    .width(dividerWidth)
                    .fillMaxHeight()
                    .pointerInput(totalWidthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (totalWidthPx > 0f) {
                                    val deltaFraction = dragAmount / totalWidthPx
                                    splitFraction = (splitFraction + deltaFraction)
                                        .coerceIn(minSplitFraction, maxSplitFraction)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(if (isDragging) Color(0xFF4F46E5) else Color(0xFFCBD5E1))
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDragging) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                    shadowElevation = if (isDragging) 3.dp else 1.dp,
                    modifier = Modifier
                        .width(12.dp)
                        .height(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .background(
                                            if (isDragging) Color.White else Color(0xFF64748B),
                                            shape = RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Right Pane
            Box(
                modifier = Modifier
                    .width(secondPaneWidth)
                    .fillMaxHeight()
            ) {
                secondPane(Modifier.fillMaxSize())
            }
        }
    }
}
