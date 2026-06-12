package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.font.Font
import com.example.R

// High contrast drop shadow to give text a crisp black outline and premium dimensional effect
val HomaTextShadow = Shadow(
    color = Color(0xC0000000), // Rich dark black shadow outline
    offset = Offset(2f, 2.5f),
    blurRadius = 4f
)

// Loaded premium Farsi typography font
val PersianFontFamily = FontFamily(
    Font(R.font.bhoma, FontWeight.Normal)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = PersianFontFamily,
        fontWeight = FontWeight.Black, // Very thick stroke emulating "Homa"
        fontSize = 32.sp,
        lineHeight = 44.sp,
        shadow = HomaTextShadow
    ),
    titleLarge = TextStyle(
        fontFamily = PersianFontFamily,
        fontWeight = FontWeight.ExtraBold, // Thick heading
        fontSize = 22.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        shadow = HomaTextShadow
    ),
    titleMedium = TextStyle(
        fontFamily = PersianFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PersianFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 24.sp, // Safe spacing preventing overlap of Persian diacritics
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PersianFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PersianFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PersianFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
