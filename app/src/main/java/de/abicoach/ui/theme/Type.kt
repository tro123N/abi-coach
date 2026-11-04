package de.abicoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The phone's own system font (Roboto, on OPPO phones OPPO Sans) with its real weights – bold titles look
 * crisp instead of artificially thickened.
 */
val appFont: FontFamily = FontFamily.Default

private val base = TextStyle(fontFamily = appFont, fontWeight = FontWeight.Normal)

val Typography =
    Typography(
        bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
        bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
        titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium),
        labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, fontWeight = FontWeight.Medium),
    )
