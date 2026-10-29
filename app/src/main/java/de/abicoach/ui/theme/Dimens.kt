/*
AbiCoach – Vorbereitung auf die mündliche Abiturprüfung Englisch (NRW)
Größen nach Bildschirmhöhe wie in Mental Math Plus (tro123N), basierend auf Mental Math,
Copyright (C) 2025-2026 HeldDerTierwelt. Lizenz: GNU GPL v3 oder neuer.
*/

package de.abicoach.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Alle Schrift- und Bauteilgrößen wachsen mit der Bildschirmhöhe, wie in Mental Math Plus. */
@Immutable
data class Dimens(
    val fonts: FontSizes,
    val icons: IconSizes,
    val components: ComponentSizes,
) {
    companion object {
        fun from(screenHeight: Dp): Dimens {
            // Sehr kleine oder unbekannte Höhen (z. B. beim ersten Messen) auf ein normales Handy setzen.
            val h = if (screenHeight.value < 480f) 800f else screenHeight.value

            fun dp(factor: Float) = (factor * h).dp

            fun sp(factor: Float) = (factor * h).sp

            return Dimens(
                fonts =
                    FontSizes(
                        title = sp(0.031f),
                        actionButton = sp(0.038f),
                        popUpDescription = sp(0.018f),
                        caption = sp(0.015f),
                        label = sp(0.024f),
                        body = sp(0.020f),
                        reading = sp(0.0195f),
                        big = sp(0.057f),
                        huge = sp(0.075f),
                    ),
                icons =
                    IconSizes(
                        topBarAction = dp(0.038f),
                        actionButton = dp(0.057f),
                        popUp = dp(0.1f),
                        menuItem = dp(0.032f),
                        status = dp(0.032f),
                        cardHeader = dp(0.038f),
                        scoreHeader = dp(0.08f),
                    ),
                components =
                    ComponentSizes(
                        button = dp(0.09f),
                        screenPadding = dp(0.024f),
                        cornerRadius = 16.dp,
                        cardCornerRadius = 32.dp,
                        dividerThickness = 2.dp,
                    ),
            )
        }
    }
}

@Immutable
data class FontSizes(
    val title: TextUnit,
    val actionButton: TextUnit,
    val popUpDescription: TextUnit,
    val caption: TextUnit,
    /** Überschriften in Karten, Knopftexte (in Mental Math Plus „settingsLabel“). */
    val label: TextUnit,
    /** Fließtext (in Mental Math Plus „sideSheetEntry“). */
    val body: TextUnit,
    /** Prüfungstexte zum Lesen. */
    val reading: TextUnit,
    /** Große Zahlen, z. B. Punkte. */
    val big: TextUnit,
    val huge: TextUnit,
)

@Immutable
data class IconSizes(
    val topBarAction: Dp,
    val actionButton: Dp,
    val popUp: Dp,
    val menuItem: Dp,
    val status: Dp,
    val cardHeader: Dp,
    val scoreHeader: Dp,
)

@Immutable
data class ComponentSizes(
    val button: Dp,
    val screenPadding: Dp,
    val cornerRadius: Dp,
    val cardCornerRadius: Dp,
    val dividerThickness: Dp,
)

val LocalDimens = staticCompositionLocalOf<Dimens> { error("No Dimens provided") }
