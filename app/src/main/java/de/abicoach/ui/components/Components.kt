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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.sp
import de.abicoach.R
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow

/** Obere Leiste im Stil der App mit optionalem Zurück-Pfeil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbiTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    backIcon: Int = R.drawable.arrow_back_24dp,
    backDescription: String = "Zurück",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dimens = AbiTheme.dimens
    TopAppBar(
        title = {
            Text(
                text = title,
                fontSize = dimens.fonts.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 8.dp).testTag("back"),
                ) {
                    Icon(
                        painter = painterResource(backIcon),
                        contentDescription = backDescription,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(dimens.icons.topBarAction),
                    )
                }
            }
        },
        actions = actions,
    )
}

/** Symbol-Knopf für die obere Leiste. */
@Composable
fun TopBarAction(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onPrimary,
    tag: String? = null,
) {
    IconButton(onClick = onClick, modifier = if (tag != null) Modifier.testTag(tag) else Modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(AbiTheme.dimens.icons.topBarAction),
        )
    }
}

/** Abgerundete Karte wie in der Ergebnisansicht der App. */
@Composable
fun AbiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = AbiTheme.dimens
    val shape = RoundedCornerShape(dimens.components.cardCornerRadius)
    val colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    val stroke = border?.let { BorderStroke(2.dp, it) }
    if (onClick != null) {
        Card(onClick = onClick, shape = shape, modifier = modifier.fillMaxWidth(), colors = colors, border = stroke) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
        }
    } else {
        Card(shape = shape, modifier = modifier.fillMaxWidth(), colors = colors, border = stroke) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), content = content)
        }
    }
}

/** Großer Knopf über die ganze Breite, Symbol links – wie der Start-Knopf der App. */
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
    container: Color = MaterialTheme.colorScheme.primary,
) {
    val dimens = AbiTheme.dimens
    val contentAlpha = if (enabled) 1f else 0.38f
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(dimens.components.cornerRadius * 2),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = container,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = container.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = dimens.components.button * 0.8f),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconTint.copy(alpha = contentAlpha * iconTint.alpha),
                modifier = Modifier.size(dimens.icons.menuItem),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    fontSize = dimens.fonts.label,
                    lineHeight = dimens.fonts.label * 1.2f,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = dimens.fonts.caption,
                        lineHeight = dimens.fonts.caption * 1.3f,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f * contentAlpha),
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailing, fontSize = dimens.fonts.caption, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
            }
        }
    }
}

/** Der große Start-Knopf: grünes Play-Symbol links, Text in der Mitte. */
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
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = RoundedCornerShape(dimens.components.cornerRadius * 2),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
            ),
        modifier = modifier.fillMaxWidth().height(dimens.components.button),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(dimens.icons.actionButton),
                tint = if (!enabled) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f) else iconTint,
            )
            Text(
                text = text,
                fontSize = dimens.fonts.actionButton * 0.8f,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = dimens.icons.actionButton),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (!enabled) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onPrimary,
            )
        }
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
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icons.menuItem * 0.85f))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            fontSize = dimens.fonts.label,
            lineHeight = dimens.fonts.label * 1.2f,
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
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .background(color.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(dimens.icons.menuItem))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
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
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .background(color.copy(alpha = 0.14f))
                .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(dimens.components.cornerRadius))
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(dimens.icons.menuItem * 0.8f))
        Spacer(Modifier.width(12.dp))
        BodyText(text, modifier = Modifier.weight(1f))
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(R.drawable.close_24dp), contentDescription = "Schließen", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

/** Kleiner Knopf mit Symbol und Beschriftung, z. B. drei nebeneinander. */
@Composable
fun MiniAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = accent,
) {
    val dimens = AbiTheme.dimens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .background(MaterialTheme.colorScheme.background)
                .clickable { onClick() }
                .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(dimens.icons.menuItem))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = dimens.fonts.caption,
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
                .clip(RoundedCornerShape(dimens.components.cornerRadius))
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(dimens.icons.menuItem * 0.8f))
            Spacer(Modifier.height(2.dp))
        }
        Text(value, fontSize = dimens.fonts.label, color = color, maxLines = 1)
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
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(dimens.components.cornerRadius))
                        .background(if (active) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.background)
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(dimens.components.cornerRadius),
                        )
                        .clickable { onSelect(option) }
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
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
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
                Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(dimens.icons.menuItem * 0.85f))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = dimens.fonts.label, lineHeight = dimens.fonts.label * 1.2f, color = MaterialTheme.colorScheme.onPrimary)
                if (subtitle != null) CaptionText(subtitle)
            }
            Icon(
                painter = painterResource(if (open) R.drawable.keyboard_arrow_up_24dp else R.drawable.keyboard_arrow_down_24dp),
                contentDescription = if (open) "Zuklappen" else "Aufklappen",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(dimens.icons.menuItem),
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
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onPrimary,
        textContentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

/** Die vier Bereiche der App. */
enum class Tab(val title: String, val icon: Int, val tag: String) {
    WEG("Abi-Weg", R.drawable.route_24dp, "tab_weg"),
    TRAINING("Training", R.drawable.record_voice_over_24dp, "tab_training"),
    KURS("Mein Kurs", R.drawable.school_24dp, "tab_kurs"),
    STATS("Statistik", R.drawable.bar_chart_24dp, "tab_stats"),
}

/** Leiste unten zum Wechseln zwischen den Bereichen, wie in Mental Math Plus. */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit, badge: (Tab) -> Boolean = { false }) {
    Surface(color = MaterialTheme.colorScheme.primary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 8.dp),
        ) {
            Tab.entries.forEach { tab ->
                BarItem(
                    tab = tab,
                    selected = tab == current,
                    showBadge = badge(tab),
                    onClick = { if (tab != current) onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, showBadge: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (selected) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxHeight()
                .clipToBounds()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() }
                .testTag(tab.tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Icon(painter = painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            if (showBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(start = 18.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(red),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(text = tab.title, fontSize = 12.sp, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** Großer Kreis mit einer Zahl darin, z. B. die Punktprognose. */
@Composable
fun BigNumber(
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = AbiTheme.dimens.components.button * 1.25f,
) {
    val dimens = AbiTheme.dimens
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .border(4.dp, color, CircleShape)
                .background(MaterialTheme.colorScheme.background, CircleShape),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = dimens.fonts.big * 0.75f, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, fontSize = dimens.fonts.caption * 0.85f, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}
