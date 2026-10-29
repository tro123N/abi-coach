package de.abicoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.abicoach.R

val roboto = FontFamily(Font(R.font.roboto_regular, FontWeight.Normal))

private val base = TextStyle(fontFamily = roboto, fontWeight = FontWeight.Normal)

val Typography =
    Typography(
        bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    )
