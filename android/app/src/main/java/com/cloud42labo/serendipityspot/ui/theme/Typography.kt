package com.cloud42labo.serendipityspot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    )
)
