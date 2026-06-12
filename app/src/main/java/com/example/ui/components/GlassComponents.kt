package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.R

/**
 * Reusable Glassmorphism transparent container that adapts beautiful frosted glow colors.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.primary.hashCode() % 2 == 0 // soft heuristic
    val glassBgColor = if (isDark) {
        Color(0x0DFFFFFF) // Tailwind bg-white/5 precisely
    } else {
        Color(0xCCFFFFFF) // Frosted white glass translucent light
    }

    val glassBorderColor = if (isDark) {
        Color(0x1BFFFFFF) // Tailwind border-white/10 precisely
    } else {
        Color(0x15000000)
    }

    Card(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(cornerRadius),
                clip = false,
                ambientColor = Color(0x05000000),
                spotColor = Color(0x10000000)
            ),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(containerColor = glassBgColor),
        border = BorderStroke(borderWidth, Brush.linearGradient(listOf(glassBorderColor, Color(0x05FFFFFF)))),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Animated premium glow/liquid connect button for Apple iOS visual design.
 */
@Composable
fun ConnectionGlowButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlow")
    
    // Smooth breathing scale animation for pulse glow outer ring
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowScale"
    )

    val mainColor = when {
        isConnected -> Color(0xFF34C759)   // Pure emerald green
        isConnecting -> Color(0xFFFFCC00)  // Gold warning connection state
        else -> Color(0xFF335DF7)          // TipsTop Brand Blue!
    }

    val pulseColor = mainColor.copy(alpha = 0.12f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp) // Generous sizing for negative breathing bounds
    ) {
        // Shimmering backdrop pulse layer
        Box(
            modifier = Modifier
                .size(190.dp)
                .drawBehind {
                    drawCircle(
                        color = pulseColor,
                        radius = (size.minDimension / 1.8f) * scaleFactor
                    )
                    // Double halo for premium depth
                    drawCircle(
                        color = pulseColor.copy(alpha = 0.05f),
                        radius = (size.minDimension / 1.4f) * scaleFactor
                    )
                }
        )

        // Custom responsive Action Button (rounded glass layout)
        Box(
            modifier = Modifier
                .size(144.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(100.dp),
                    clip = false,
                    ambientColor = mainColor.copy(alpha = 0.2f),
                    spotColor = mainColor.copy(alpha = 0.35f)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color(0x33FFFFFF), Color(0x15FFFFFF))
                    ),
                    shape = RoundedCornerShape(100.dp)
                )
                .clip(RoundedCornerShape(100.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x1BFFFFFF), Color(0x0AFFFFFF))
                    )
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Circular active center containing power icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(mainColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = "Power Mode Switch",
                        tint = mainColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isConnected) {
                        stringResource(R.string.status_connected).uppercase()
                    } else if (isConnecting) {
                        stringResource(R.string.status_connecting).uppercase()
                    } else {
                        stringResource(R.string.connect).uppercase()
                    },
                    color = if (isConnected) Color(0xFFE2E8F0) else if (isConnecting) Color(0xFFFEF08A) else Color(0xFF93C5FD),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}
