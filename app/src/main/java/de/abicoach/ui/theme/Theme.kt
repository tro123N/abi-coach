/*
AbiCoach – Vorbereitung auf die mündliche Abiturprüfung Englisch (NRW)
Ursprünglich im Design von Mental Math Plus (tro123N), basierend auf Mental Math,
Copyright (C) 2025-2026 HeldDerTierwelt. Seit Version 21 im „Living Flow“-Stil:
Glas-Karten über einem weich fließenden Farbhintergrund.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0.md.
*/

package de.abicoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Hell, dunkel oder wie das System. */
enum class ThemeMode(val label: String) {
    SYSTEM("Wie das System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
}

/** Farbthemen: jedes hat eigene Lichtfarben für den fließenden Hintergrund. */
enum class Palette(val label: String) {
    STANDARD("Standard"),
    OZEAN("Ozean"),
    WALD("Wald"),
    ABENDROT("Abendrot"),
    LAVENDEL("Lavendel"),
    SCHWARZ("Schwarz"),
}

val green = Color(0xFF39A241)
val red = Color(0xFFD34747)
val yellow = Color(0xFFFBC02D)
val blue = Color(0xFF1976D2)

/**
 * Everything the Living Flow look needs: the page base, three soft light colours that flow behind the content,
 * frosted glass for cards, the light edge of the glass and the accent pair for gradients.
 */
@Immutable
data class Flow(
    val dark: Boolean,
    /** Page colour under the light. */
    val base: Color,
    /** The three soft colour lights of the background. */
    val lights: List<Color>,
    /** Frosted glass of cards and buttons. */
    val glass: Color,
    /** The same glass, but opaque: dialogs, menus and bars on phones without blur. */
    val solid: Color,
    /** Inner area on a glass card (rows, chips, small tiles). */
    val tile: Color,
    /** Light edge of the glass: bright on top, faint at the bottom. */
    val edgeTop: Color,
    val edgeBottom: Color,
    /** Soft shadow under floating glass. */
    val shadow: Color,
    val text: Color,
    val subtle: Color,
    val accent: Color,
    /** Second colour of the accent gradient (hero cards, the start button). */
    val accent2: Color,
    /** The gradient of hero cards and the start button; a little deeper in dark mode so white text stays clear. */
    val hero1: Color,
    val hero2: Color,
)

private class Spec(
    val base: Long,
    val lights: List<Pair<Long, Float>>,
    val text: Long,
    val accent: Long,
    val accent2: Long,
    val solid: Long,
)

private fun spec(palette: Palette, dark: Boolean): Spec =
    when (palette) {
        Palette.STANDARD ->
            if (dark) {
                Spec(0xFF0B0F1A, listOf(0xFF2F6BFF to 0.30f, 0xFF7B61FF to 0.26f, 0xFF22C1C3 to 0.18f), 0xFFEEF2FA, 0xFF6E9BFF, 0xFF9B87FF, 0xFF1A2031)
            } else {
                Spec(0xFFEEF1F7, listOf(0xFF2F6BFF to 0.34f, 0xFF7B61FF to 0.26f, 0xFF22C1C3 to 0.24f), 0xFF1C2333, 0xFF2F6BFF, 0xFF7B61FF, 0xFFFBFCFE)
            }
        Palette.OZEAN ->
            if (dark) {
                Spec(0xFF08121C, listOf(0xFF1E88E5 to 0.28f, 0xFF00ACC1 to 0.22f, 0xFF5C6BC0 to 0.16f), 0xFFE3EEF7, 0xFF4FA3F0, 0xFF4DD0E1, 0xFF142231)
            } else {
                Spec(0xFFE6EEF6, listOf(0xFF1E88E5 to 0.32f, 0xFF00ACC1 to 0.26f, 0xFF5C6BC0 to 0.20f), 0xFF22344A, 0xFF1565C0, 0xFF00838F, 0xFFFBFDFF)
            }
        Palette.WALD ->
            if (dark) {
                Spec(0xFF09130D, listOf(0xFF00897B to 0.26f, 0xFF7CB342 to 0.20f, 0xFF26A69A to 0.16f), 0xFFE3EFE6, 0xFF3DB8A8, 0xFF9CCC65, 0xFF17231B)
            } else {
                Spec(0xFFE7F0E9, listOf(0xFF00897B to 0.30f, 0xFF7CB342 to 0.26f, 0xFF26A69A to 0.20f), 0xFF243A2B, 0xFF00796B, 0xFF558B2F, 0xFFFBFDFB)
            }
        Palette.ABENDROT ->
            if (dark) {
                Spec(0xFF150D0A, listOf(0xFFF4511E to 0.26f, 0xFFFFB300 to 0.18f, 0xFFEC407A to 0.16f), 0xFFF4E8E1, 0xFFFF8A50, 0xFFF48FB1, 0xFF2A1C16)
            } else {
                Spec(0xFFF6ECE6, listOf(0xFFF4511E to 0.28f, 0xFFFFB300 to 0.26f, 0xFFEC407A to 0.20f), 0xFF45312A, 0xFFD84315, 0xFFC2185B, 0xFFFFFCFA)
            }
        Palette.LAVENDEL ->
            if (dark) {
                Spec(0xFF100C18, listOf(0xFF7E57C2 to 0.30f, 0xFFEC407A to 0.18f, 0xFF5C6BC0 to 0.18f), 0xFFEDE7F6, 0xFFB39DDB, 0xFFF48FB1, 0xFF221C30)
            } else {
                Spec(0xFFEFEAF6, listOf(0xFF7E57C2 to 0.30f, 0xFFEC407A to 0.22f, 0xFF5C6BC0 to 0.22f), 0xFF392E4C, 0xFF7E57C2, 0xFFD81B60, 0xFFFDFBFF)
            }
        Palette.SCHWARZ ->
            if (dark) {
                Spec(0xFF000000, listOf(0xFF2E8CE6 to 0.18f, 0xFF7B61FF to 0.14f, 0xFF22C1C3 to 0.10f), 0xFFEDEDED, 0xFF2E8CE6, 0xFF8C7BFF, 0xFF121212)
            } else {
                spec(Palette.STANDARD, false)
            }
    }

/** The Living Flow colours of a palette in light or dark mode. */
fun flowFor(palette: Palette, dark: Boolean): Flow {
    val s = spec(palette, dark)
    val text = Color(s.text)
    val solid = Color(s.solid)
    return Flow(
        dark = dark,
        base = Color(s.base),
        lights = s.lights.map { (c, a) -> Color(c).copy(alpha = a) },
        glass = if (dark) solid.copy(alpha = if (palette == Palette.SCHWARZ) 0.74f else 0.62f) else Color.White.copy(alpha = 0.66f),
        solid = solid,
        tile = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.62f),
        edgeTop = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.95f),
        edgeBottom = if (dark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.35f),
        shadow = if (dark) Color.Black.copy(alpha = 0.38f) else text.copy(alpha = 0.10f),
        text = text,
        subtle = text.copy(alpha = 0.66f),
        accent = Color(s.accent),
        accent2 = Color(s.accent2),
        hero1 = if (dark) androidx.compose.ui.graphics.lerp(Color(s.accent), Color.Black, 0.3f) else Color(s.accent),
        hero2 = if (dark) androidx.compose.ui.graphics.lerp(Color(s.accent2), Color.Black, 0.3f) else Color(s.accent2),
    )
}

/**
 * The Material colour roles are mapped onto the glass look, so every existing screen follows it:
 * `primary` is the opaque card colour (dialogs), `onPrimary` the text, `background` the inner glass tile.
 */
private fun colorSchemeFor(flow: Flow) =
    if (flow.dark) {
        darkColorScheme(
            primary = flow.solid,
            onPrimary = flow.text,
            secondary = flow.subtle,
            onSecondary = flow.base,
            tertiary = flow.subtle,
            onTertiary = flow.text,
            background = flow.tile,
            onBackground = flow.text,
            surface = flow.solid,
            onSurface = flow.text,
            surfaceVariant = flow.solid,
            onSurfaceVariant = flow.subtle,
            surfaceContainerLowest = flow.base,
            surfaceContainerLow = flow.solid,
            surfaceContainer = flow.solid,
            surfaceContainerHigh = flow.solid,
            surfaceContainerHighest = flow.solid,
            surfaceBright = flow.solid,
            surfaceDim = flow.base,
            outline = flow.text.copy(alpha = 0.3f),
            outlineVariant = flow.text.copy(alpha = 0.14f),
            inverseSurface = flow.text,
            inverseOnSurface = flow.base,
        )
    } else {
        lightColorScheme(
            primary = flow.solid,
            onPrimary = flow.text,
            secondary = flow.subtle,
            onSecondary = Color.White,
            tertiary = flow.subtle,
            onTertiary = flow.text,
            background = flow.tile,
            onBackground = flow.text,
            surface = flow.solid,
            onSurface = flow.text,
            surfaceVariant = flow.solid,
            onSurfaceVariant = flow.subtle,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = flow.solid,
            surfaceContainer = flow.solid,
            surfaceContainerHigh = flow.solid,
            surfaceContainerHighest = flow.solid,
            surfaceBright = flow.solid,
            surfaceDim = flow.base,
            outline = flow.text.copy(alpha = 0.3f),
            outlineVariant = flow.text.copy(alpha = 0.12f),
            inverseSurface = flow.text,
            inverseOnSurface = Color.White,
        )
    }

/** Vorschaufarben eines Farbthemas: Hintergrund, Karte und Akzent. */
fun palettePreview(palette: Palette, dark: Boolean): Triple<Color, Color, Color> {
    val f = flowFor(palette, dark)
    return Triple(f.lights.first().compositeOver(f.base), f.glass.compositeOver(f.base), f.accent)
}

/** Akzentfarbe des Farbthemas (Knöpfe, Markierungen). */
val LocalAccent = staticCompositionLocalOf { blue }

/** Die Glas- und Lichtfarben des aktuellen Farbthemas. */
val LocalFlow = staticCompositionLocalOf { flowFor(Palette.STANDARD, false) }

/** Akzentfarbe des aktuellen Farbthemas. */
val accent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAccent.current

/** Ist gerade der dunkle Modus aktiv? */
val LocalDarkTheme = staticCompositionLocalOf { false }

fun isDark(mode: ThemeMode, system: Boolean): Boolean =
    when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> system
    }

@Composable
fun AbiTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: Palette = Palette.STANDARD,
    content: @Composable () -> Unit,
) {
    val darkTheme = isDark(themeMode, isSystemInDarkTheme())
    val flow = remember(palette, darkTheme) { flowFor(palette, darkTheme) }
    val colorScheme = remember(flow) { colorSchemeFor(flow) }
    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val dimens = remember(screenHeight) { Dimens.from(screenHeight) }

    CompositionLocalProvider(
        LocalDimens provides dimens,
        LocalAccent provides flow.accent,
        LocalDarkTheme provides darkTheme,
        LocalFlow provides flow,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

object AbiTheme {
    val dimens: Dimens
        @Composable
        @ReadOnlyComposable
        get() = LocalDimens.current

    val accent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalAccent.current

    val flow: Flow
        @Composable
        @ReadOnlyComposable
        get() = LocalFlow.current
}

/** Farbe für eine Punktzahl 0–15: grün ab 13, Akzent ab 10, gelb ab 7, sonst rot. */
@Composable
@ReadOnlyComposable
fun pointsColor(points: Double): Color =
    when {
        points >= 12.5 -> green
        points >= 9.5 -> LocalAccent.current
        points >= 6.5 -> yellow
        else -> red
    }

/**
 * A colour that stays readable as an icon or text on light glass: very bright colours (yellow) are darkened
 * in light mode; in dark mode the colour stays as it is.
 */
@Composable
@ReadOnlyComposable
fun readable(color: Color): Color {
    if (LocalDarkTheme.current || color.alpha < 0.5f) return color
    val luminance = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    return if (luminance > 0.55f) androidx.compose.ui.graphics.lerp(color, Color.Black, 0.38f) else color
}
