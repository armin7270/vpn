package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Highly responsive canvas-drawn brand icon representing the "TipsTop Network" logo.
 * It features the three ascending stegano-barred channels sliced by a diagonal slot,
 * built perfectly with raw geometry.
 */
@Composable
fun TipsTopLogo(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    showGrayBorder: Boolean = true // True for Logo 2, False for Logo 1
) {
    // Exact brand colors matching the visual logo
    val logoBlue = Color(0xFF335DF7)
    val frameBorderColor = Color(0xFF5A606F) // Sleek slate gray border from Logo 2

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp))
            .background(Color.White, shape = RoundedCornerShape(12.dp))
            .border(
                width = if (showGrayBorder) 2.dp else 0.dp,
                color = if (showGrayBorder) frameBorderColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Column 1 (Left) - Bottom portion is flat, top sloped up-right
            val pathCol1 = Path().apply {
                moveTo(w * 0.14f, h * 0.82f) // Bottom-left
                lineTo(w * 0.14f, h * 0.54f) // Top-left
                lineTo(w * 0.36f, h * 0.45f) // Top-right (slanted)
                lineTo(w * 0.36f, h * 0.82f) // Bottom-right
                close()
            }

            // 2. Horizontal bridge connecting Column 1 and Column 2 at baseline
            val pathBridge = Path().apply {
                moveTo(w * 0.36f, h * 0.68f) // Upper-left corner of bridge
                lineTo(w * 0.50f, h * 0.68f) // Upper-right corner
                lineTo(w * 0.50f, h * 0.82f) // Bottom-right corner
                lineTo(w * 0.36f, h * 0.82f) // Bottom-left corner
                close()
            }

            // 3. Column 2 (Middle, Upper portion above diagonal slot)
            val pathCol2Upper = Path().apply {
                moveTo(w * 0.42f, h * 0.40f) // Left-bottom of upper piece
                lineTo(w * 0.42f, h * 0.33f) // Left-top of upper piece
                lineTo(w * 0.64f, h * 0.24f) // Right-top
                lineTo(w * 0.64f, h * 0.51f) // Right-bottom
                close()
            }

            // 4. Column 2 (Middle, Lower portion below diagonal slot)
            val pathCol2Lower = Path().apply {
                moveTo(w * 0.42f, h * 0.67f) // Left-top of lower piece
                lineTo(w * 0.64f, h * 0.58f) // Right-top of lower piece
                lineTo(w * 0.64f, h * 0.82f) // Right-bottom
                lineTo(w * 0.42f, h * 0.82f) // Left-bottom
                close()
            }

            // 5. Column 3 (Right, Upper portion above diagonal slot)
            val pathCol3Upper = Path().apply {
                moveTo(w * 0.70f, h * 0.28f) // Left-bottom of upper piece
                lineTo(w * 0.70f, h * 0.15f) // Left-top
                lineTo(w * 0.92f, h * 0.06f) // Right-top (maximum peak)
                lineTo(w * 0.92f, h * 0.39f) // Right-bottom
                close()
            }

            // 6. Column 3 (Right, Lower portion below diagonal slot)
            val pathCol3Lower = Path().apply {
                moveTo(w * 0.70f, h * 0.55f) // Left-top of lower piece
                lineTo(w * 0.92f, h * 0.46f) // Right-top of lower piece
                lineTo(w * 0.92f, h * 0.73f) // Right-bottom
                lineTo(w * 0.70f, h * 0.73f) // Left-bottom
                close()
            }

            // Draw paths cleanly
            drawPath(path = pathCol1, color = logoBlue, style = Fill)
            drawPath(path = pathBridge, color = logoBlue, style = Fill)
            drawPath(path = pathCol2Upper, color = logoBlue, style = Fill)
            drawPath(path = pathCol2Lower, color = logoBlue, style = Fill)
            drawPath(path = pathCol3Upper, color = logoBlue, style = Fill)
            drawPath(path = pathCol3Lower, color = logoBlue, style = Fill)
        }
    }
}
