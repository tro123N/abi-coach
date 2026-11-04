/*
AbiCoach – Vorbereitung auf die mündliche Abiturprüfung Englisch (NRW)
Bauteile im Stil von Mental Math Plus (tro123N), basierend auf Mental Math,
Copyright (C) 2025-2026 HeldDerTierwelt.

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

package de.abicoach.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.abicoach.R
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.readable
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** Obere Leiste: durchsichtig, mit rundem Glas-Knopf zum Zurückgehen und großem Titel. */
@Composable
fun AbiTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    backIcon: Int = R.drawable.arrow_back_24dp,
    backDescription: String = "Zurück",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dimens = AbiTheme.dimens
    val flow = AbiTheme.flow
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 14.dp),
    ) {
        if (onBack != null) {
            GlassIconButton(backIcon, backDescription, onBack, tag = "back")
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title,
            fontSize = dimens.fonts.title * 0.82f,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = flow.text,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Runder Glas-Knopf mit Symbol für die obere Leiste. */
@Composable
fun TopBarAction(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onPrimary,
    tag: String? = null,
) {
    GlassIconButton(icon, description, onClick, tint = tint, tag = tag)
}

/** Karte aus Milchglas mit Lichtkante und weichem Schatten. */
@Composable
fun AbiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), onClick = onClick, border = border) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), content = content)
    }
}

/**
 * Eine Zeile zum Antippen: Symbol im farbigen Kreis, Titel, kurze Erklärung. Auf einer Karte (Hintergrund
 * `colorScheme.background`) ist sie eine helle Glas-Fläche, allein steht sie als eigene Glas-Karte.
 */
@Composable
fun ActionButton(
    text: String,
    icon: Int,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
    trailing: String? = null,
    container: Color = Color.Unspecified,
) {
    val dimens = AbiTheme.dimens
    val flow = AbiTheme.flow
    val onCard = container == MaterialTheme.colorScheme.background
    val fill = if (container == Color.Unspecified) flow.glass else container
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = dimens.components.button * 0.8f)
                .squish(interaction, 0.97f)
                .glass(flow, 20.dp, fill, shadow = !onCard, edge = !onCard)
                .touchLight(interaction, flow.touch)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .alpha(if (enabled) 1f else 0.42f),
    ) {
        IconBubble(icon, iconTint, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                fontSize = dimens.fonts.body,
                lineHeight = dimens.fonts.body * 1.2f,
                fontWeight = FontWeight.Medium,
                color = flow.text,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = dimens.fonts.caption,
                    lineHeight = dimens.fonts.caption * 1.3f,
                    color = flow.subtle,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, fontSize = dimens.fonts.caption, fontWeight = FontWeight.Medium, color = flow.subtle)
        }
    }
}

/** Der große Start-Knopf: eine leuchtende Kapsel im Farbverlauf, das Symbol im weißen Kreis. */
@Composable
fun StartButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: Int = R.drawable.play_arrow_24dp,
    iconTint: Color = green,
) {
    val dimens = AbiTheme.dimens
    val flow = AbiTheme.flow
    val height = dimens.components.button
    val capsule = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .squish(interaction, 0.96f)
                .softShadow(height / 2, if (enabled) flow.hero1.copy(alpha = 0.32f) else Color.Transparent, blur = 20.dp, offsetY = 8.dp)
                .clip(capsule)
                .background(if (enabled) Brush.linearGradient(listOf(flow.hero1, flow.hero2)) else SolidColor(flow.text.copy(alpha = 0.1f)))
                .border(1.dp, Color.White.copy(alpha = if (enabled) 0.3f else 0.1f), capsule)
                .touchLight(interaction, Color.White.copy(alpha = 0.25f))
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(height * 0.64f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (enabled) 0.95f else 0.4f)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(height * 0.4f),
                tint = if (!enabled) flow.subtle else iconTint,
            )
        }
        Text(
            text = text,
            fontSize = dimens.fonts.actionButton * (if (text.length > 18) 0.56f else 0.7f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = height * 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (!enabled) flow.subtle else Color.White,
        )
    }
}

/** Drei Sterne, erreichte gelb, fehlende blass. */
@Composable
fun StarRow(stars: Int, size: Dp, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            Icon(
                painter = painterResource(R.drawable.star_24dp),
                contentDescription = null,
                tint = if (i < stars) yellow else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Runde Plakette eines Levels oder Schritts: Nummer, Schloss, Haken oder Auffrischen. */
@Composable
fun LevelBadge(
    label: String,
    locked: Boolean,
    mastered: Boolean,
    attention: Boolean,
    highlighted: Boolean,
    size: Dp,
    icon: Int? = null,
) {
    val ringColor =
        when {
            attention -> yellow
            highlighted -> accent
            mastered -> green
            else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .alpha(if (locked) 0.45f else 1f)
                .border(width = 3.dp, color = ringColor, shape = CircleShape)
                .background(MaterialTheme.colorScheme.background, CircleShape),
    ) {
        when {
            locked ->
                Icon(
                    painter = painterResource(R.drawable.lock_24dp),
                    contentDescription = "gesperrt",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.45f),
                )
            attention ->
                Icon(
                    painter = painterResource(R.drawable.refresh_24dp),
                    contentDescription = "auffrischen",
                    tint = yellow,
                    modifier = Modifier.size(size * 0.5f),
                )
            mastered && icon == null ->
                Icon(
                    painter = painterResource(R.drawable.check_24dp),
                    contentDescription = "geschafft",
                    tint = green,
                    modifier = Modifier.size(size * 0.5f),
                )
            icon != null ->
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (mastered) green else if (highlighted) accent else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.5f),
                )
            else ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontSize = AbiTheme.dimens.fonts.body,
                )
        }
    }
}

/** Kleine Überschrift innerhalb einer Karte. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, icon: Int? = null, iconTint: Color = accent) {
    val dimens = AbiTheme.dimens
    Row(modifier = modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = readable(iconTint), modifier = Modifier.size(dimens.icons.menuItem * 0.8f))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            fontSize = dimens.fonts.label * 0.9f,
            lineHeight = dimens.fonts.label * 1.1f,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Fließtext in einer Karte. */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onPrimary,
    fontWeight: FontWeight? = null,
) {
    val dimens = AbiTheme.dimens
    Text(
        text = text,
        fontSize = dimens.fonts.body,
        lineHeight = dimens.fonts.body * 1.35f,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}

/** Kleine graue Zusatzzeile. */
@Composable
fun CaptionText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val dimens = AbiTheme.dimens
    Text(
        text = text,
        fontSize = dimens.fonts.caption,
        lineHeight = dimens.fonts.caption * 1.35f,
        color = color ?: MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        modifier = modifier,
    )
}

/** Abstand zwischen Karten. */
@Composable
fun CardGap() = Spacer(Modifier.height(16.dp))

/** Kleiner Abstand innerhalb einer Karte. */
@Composable
fun Gap(height: Dp = 8.dp) = Spacer(Modifier.height(height))

/** Kurze Erklärung oben in einem Bereich: wofür er da ist. */
@Composable
fun AreaIntro(icon: Int, title: String, text: String, modifier: Modifier = Modifier, color: Color = accent) {
    val dimens = AbiTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(color.copy(alpha = 0.10f))
                .border(1.dp, color.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        IconBubble(icon, color, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, fontSize = dimens.fonts.body, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
            CaptionText(text)
        }
    }
}

/** Hinweisband: gelb für Hinweise, rot für Fehler, grün für Erfolg. */
@Composable
fun Banner(text: String, color: Color, modifier: Modifier = Modifier, icon: Int = R.drawable.info_24dp, onClose: (() -> Unit)? = null) {
    val dimens = AbiTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.14f))
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = readable(color), modifier = Modifier.size(dimens.icons.menuItem * 0.8f))
        Spacer(Modifier.width(12.dp))
        BodyText(text, modifier = Modifier.weight(1f))
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.close_24dp), contentDescription = "Schließen", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** Kleine Kachel mit Symbol und Beschriftung, z. B. drei nebeneinander; optional mit rotem Zähler. */
@Composable
fun MiniAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = accent,
    badge: Int = 0,
    tag: String? = null,
) {
    val dimens = AbiTheme.dimens
    val flow = AbiTheme.flow
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .let { if (tag != null) it.testTag(tag) else it }
                .squish(interaction, 0.92f)
                .glass(flow, 20.dp, MaterialTheme.colorScheme.background, shadow = false, edge = true)
                .touchLight(interaction, flow.touch)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Box {
            Icon(painter = painterResource(icon), contentDescription = null, tint = readable(tint), modifier = Modifier.size(dimens.icons.menuItem))
            if (badge > 0) {
                Text(
                    badge.toString(),
                    fontSize = dimens.fonts.caption * 0.75f,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-6).dp)
                            .clip(RoundedCornerShape(50))
                            .background(red)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = dimens.fonts.caption,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Kennzahl: großer Wert, darunter eine kurze Beschriftung. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onPrimary, icon: Int? = null) {
    val dimens = AbiTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = readable(color), modifier = Modifier.size(dimens.icons.menuItem * 0.8f))
            Spacer(Modifier.height(2.dp))
        }
        Text(value, fontSize = dimens.fonts.label, fontWeight = FontWeight.Bold, color = readable(color), maxLines = 1)
        CaptionText(label, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Schalter in den Farben des Farbthemas. */
@Composable
fun AbiSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                uncheckedTrackColor = MaterialTheme.colorScheme.background,
                uncheckedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f),
            ),
        modifier = modifier,
    )
}

/** Zeile mit Text links und Schalter rechts. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val dimens = AbiTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = dimens.fonts.body,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.5f),
            )
            if (subtitle != null) CaptionText(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        AbiSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Auswahl aus wenigen Möglichkeiten als Chips; die gewählte ist in der Akzentfarbe hinterlegt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "choice",
) {
    val dimens = AbiTheme.dimens
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = option == selected
            Text(
                text = label(option),
                fontSize = dimens.fonts.caption * 1.1f,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) readable(accent) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background)
                        .border(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        )
                        .clickable { onSelect(option) }
                        .testTag("${tagPrefix}_$index")
                        .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** Like [ChoiceChips], but several can be on at once: a tap switches one on or off. [canAdd] false greys out the rest. */
@Composable
fun <T> MultiChoiceChips(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    canAdd: Boolean = true,
    tagPrefix: String = "multi",
) {
    val dimens = AbiTheme.dimens
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val active = option in selected
            val usable = active || canAdd
            Text(
                text = (if (active) "✓ " else "") + label(option),
                fontSize = dimens.fonts.caption * 1.1f,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color =
                    when {
                        active -> readable(accent)
                        usable -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f)
                    },
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background)
                        .border(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) accent.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(50),
                        )
                        .clickable(enabled = usable) { onToggle(option) }
                        .testTag("${tagPrefix}_$index")
                        .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/** Kleine farbige Marke, z. B. „AFB II“ oder ein Themenfeld. */
@Composable
fun Pill(text: String, color: Color = accent, modifier: Modifier = Modifier) {
    val dimens = AbiTheme.dimens
    Text(
        text = text,
        fontSize = dimens.fonts.caption * 0.92f,
        fontWeight = FontWeight.Medium,
        color = readable(color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Mehrere Marken nebeneinander, bei Bedarf umgebrochen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PillRow(items: List<String>, color: Color = accent, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { Pill(it, color) }
    }
}

/** Eingabefeld im Stil der App. */
@Composable
fun AbiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    tag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(AbiTheme.dimens.components.cornerRadius),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                focusedBorderColor = accent,
                focusedLabelColor = accent,
                cursorColor = accent,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
            ),
        modifier = modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it },
    )
}

/** Karte mit Kopfzeile, die sich auf- und zuklappen lässt. */
@Composable
fun ExpandableCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
    iconTint: Color = accent,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "expand")
    val dimens = AbiTheme.dimens
    AbiCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.components.cornerRadius))
                    .clickable { open = !open },
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, tint = readable(iconTint), modifier = Modifier.size(dimens.icons.menuItem * 0.8f))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = dimens.fonts.label * 0.9f, lineHeight = dimens.fonts.label * 1.1f, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                if (subtitle != null) CaptionText(subtitle)
            }
            Icon(
                painter = painterResource(R.drawable.keyboard_arrow_down_24dp),
                contentDescription = if (open) "Zuklappen" else "Aufklappen",
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(dimens.icons.menuItem).rotate(turn),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(top = 10.dp), content = content)
        }
    }
}

/** Wartekarte mit Kreisel und Text. */
@Composable
fun LoadingCard(text: String, modifier: Modifier = Modifier, detail: String? = null) {
    AbiCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                BodyText(text)
                if (detail != null) CaptionText(detail)
            }
        }
    }
}

/** Fortschrittsbalken in der Akzentfarbe. */
@Composable
fun ProgressLine(fraction: Float?, modifier: Modifier = Modifier, color: Color = accent) {
    if (fraction == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round,
        )
    } else {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
    }
}

/** Zeile mit Haken (grün) oder Kreuz (rot) und Text. */
@Composable
fun GoalRow(ok: Boolean, text: String, modifier: Modifier = Modifier, neutral: Boolean = false) {
    val dimens = AbiTheme.dimens
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(vertical = 3.dp)) {
        Icon(
            painter = painterResource(if (ok) R.drawable.check_24dp else if (neutral) R.drawable.info_24dp else R.drawable.close_24dp),
            contentDescription = null,
            tint = if (ok) green else if (neutral) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f) else red,
            modifier = Modifier.size(dimens.icons.status * 0.8f),
        )
        Spacer(Modifier.width(10.dp))
        BodyText(text, modifier = Modifier.weight(1f))
    }
}

/** Aufzählungspunkt in der Akzentfarbe. */
@Composable
fun BulletText(text: String, modifier: Modifier = Modifier, bulletColor: Color = accent) {
    val dimens = AbiTheme.dimens
    Row(modifier = modifier.padding(vertical = 2.dp)) {
        Text("•", fontSize = dimens.fonts.body, lineHeight = dimens.fonts.body * 1.35f, color = bulletColor)
        Spacer(Modifier.width(8.dp))
        BodyText(text, modifier = Modifier.weight(1f))
    }
}

/** Dialog im Stil der App. */
@Composable
fun PopUp(
    title: String,
    description: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelText: String? = "Abbrechen",
    confirmColor: Color = accent,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val dimens = AbiTheme.dimens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontSize = dimens.fonts.title * 0.85f, color = MaterialTheme.colorScheme.onPrimary) },
        text = {
            Column {
                if (description.isNotBlank()) {
                    Text(text = description, fontSize = dimens.fonts.popUpDescription, color = MaterialTheme.colorScheme.onPrimary)
                }
                if (content != null) {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor, contentColor = Color.White),
                shape = RoundedCornerShape(50),
                onClick = onConfirm,
                modifier = Modifier.testTag("popupConfirm"),
            ) { Text(text = confirmText, fontSize = dimens.fonts.popUpDescription) }
        },
        dismissButton =
            if (cancelText != null) {
                {
                    TextButton(onClick = onDismiss) {
                        Text(text = cancelText, fontSize = dimens.fonts.popUpDescription, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            } else {
                null
            },
        shape = RoundedCornerShape(28.dp),
        containerColor = AbiTheme.flow.solid,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        textContentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

/** Die vier Bereiche der App (die Namen der Einträge bleiben, damit gespeicherte Stände und Tests passen). */
enum class Tab(val title: String, val icon: Int, val tag: String) {
    WEG("Heute", R.drawable.light_mode_24dp, "tab_weg"),
    TRAINING("Üben", R.drawable.mic_24dp, "tab_training"),
    KURS("Kurs", R.drawable.school_24dp, "tab_kurs"),
    STATS("Fortschritt", R.drawable.trending_up_24dp, "tab_stats"),
}

/** Ring mit einer Zahl darin, z. B. die Punktprognose; der Bogen zeigt den Anteil von 15 Punkten. */
@Composable
fun BigNumber(
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = AbiTheme.dimens.components.button * 1.25f,
    track: Color? = null,
    textColor: Color? = null,
) {
    val dimens = AbiTheme.dimens
    val fraction = value.toFloatOrNull()?.div(15f)?.coerceIn(0f, 1f)
    // On the coloured hero card the given colours are used exactly; elsewhere they are made readable on glass.
    val ring = if (textColor != null) color else readable(color)
    val trackColor = track ?: color.copy(alpha = 0.16f)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .drawBehind {
                    val stroke = this.size.minDimension * 0.085f
                    val inset = stroke / 2
                    val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                    drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                    if (fraction != null && fraction > 0f) {
                        drawArc(ring, -90f, 360f * fraction, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = dimens.fonts.big * 0.72f, color = textColor ?: ring, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, fontSize = dimens.fonts.caption * 0.82f, color = (textColor ?: MaterialTheme.colorScheme.onPrimary).copy(alpha = 0.75f), maxLines = 1)
        }
    }
}
