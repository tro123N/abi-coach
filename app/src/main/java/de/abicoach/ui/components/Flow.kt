/*
AbiCoach – Vorbereitung auf die mündliche Abiturprüfung Englisch (NRW)
Bauteile im „Living Flow“-Stil: fließendes Licht, Milchglas, federnde Knöpfe.
Lizenz: GNU GPL v3 oder neuer.
*/

package de.abicoach.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.abicoach.R
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.Flow
import de.abicoach.ui.theme.readable
import de.abicoach.ui.theme.red

// ---------------------------------------------------------------- motion

/** Buttons, cards and tiles give way under the finger and spring back – the "flexible feedback" of Living Flow. */
fun Modifier.squish(interaction: InteractionSource, pressedScale: Float = 0.96f): Modifier =
    composed {
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) pressedScale else 1f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
            label = "squish",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

// ---------------------------------------------------------------- the flowing light

private class Light(val x: Float, val y: Float, val r: Float)

/** Where the three lights sit on each kind of page (x and y relative to the page, radius relative to its width). */
private val moods =
    listOf(
        // Heute
        listOf(Light(0.10f, 0.07f, 0.56f), Light(1.00f, 0.50f, 0.52f), Light(0.10f, 0.92f, 0.54f)),
        // Üben
        listOf(Light(0.92f, 0.06f, 0.56f), Light(0.00f, 0.60f, 0.54f), Light(0.86f, 1.00f, 0.50f)),
        // Kurs
        listOf(Light(0.50f, -0.02f, 0.60f), Light(1.02f, 0.66f, 0.50f), Light(0.00f, 0.88f, 0.52f)),
        // Fortschritt
        listOf(Light(0.08f, 0.05f, 0.56f), Light(1.00f, 0.55f, 0.54f), Light(0.05f, 0.94f, 0.50f)),
        // a page on top of an area
        listOf(Light(0.97f, 0.05f, 0.56f), Light(-0.03f, 0.62f, 0.54f), Light(0.95f, 0.96f, 0.50f)),
    )

/** Which of the palette's three light colours each light uses on that kind of page. */
private val colorOrder = listOf(listOf(0, 1, 2), listOf(2, 0, 1), listOf(1, 2, 0), listOf(0, 2, 1), listOf(1, 0, 2))

private class LightState(val x: State<Float>, val y: State<Float>, val r: State<Float>, val color: State<Color>)

/**
 * The page background: a calm base with three soft colour lights. When you switch pages the lights flow
 * slowly to their new places – nothing moves while you just read.
 */
@Composable
fun LivingBackground(mood: Int, modifier: Modifier = Modifier) {
    val flow = AbiTheme.flow
    val index = mood.coerceIn(moods.indices)
    val targets = moods[index]
    val order = colorOrder[index]
    val motion = spring<Float>(dampingRatio = 0.85f, stiffness = 38f)
    val lights =
        (0..2).map { i ->
            val t = targets[i]
            LightState(
                animateFloatAsState(t.x, motion, label = "lightX$i"),
                animateFloatAsState(t.y, motion, label = "lightY$i"),
                animateFloatAsState(t.r, motion, label = "lightR$i"),
                animateColorAsState(flow.lights[order[i]], tween(900), label = "lightC$i"),
            )
        }
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(flow.base)
                lights.forEach { l ->
                    val c = l.color.value
                    val center = Offset(l.x.value * size.width, l.y.value * size.height)
                    val radius = l.r.value * size.width * 1.25f
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                0f to c,
                                0.5f to c.copy(alpha = c.alpha * 0.45f),
                                1f to Color.Transparent,
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                    )
                }
            },
    )
}

// ---------------------------------------------------------------- glass

/** A soft shadow around a rounded shape, drawn only outside of it – so translucent glass stays clear. */
fun Modifier.softShadow(radius: Dp, color: Color, blur: Dp = 18.dp, offsetY: Dp = 6.dp): Modifier =
    if (color.alpha <= 0f) {
        this
    } else {
        drawBehind {
            val r = radius.toPx()
            val b = blur.toPx()
            val dy = offsetY.toPx()
            val outline = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
            clipPath(outline, ClipOp.Difference) {
                val steps = 7
                val layer = color.copy(alpha = (color.alpha * 1.6f / steps).coerceAtMost(1f))
                for (i in steps downTo 1) {
                    val f = i / steps.toFloat()
                    val grow = b * f
                    drawRoundRect(
                        color = layer,
                        topLeft = Offset(-grow * 0.8f, -grow * 0.5f + dy * f),
                        size = Size(size.width + grow * 1.6f, size.height + grow * 1.2f),
                        cornerRadius = CornerRadius(r + grow),
                    )
                }
            }
        }
    }

/** Frosted glass: soft shadow, translucent fill and a light edge that is bright on top. */
fun Modifier.glass(
    flow: Flow,
    radius: Dp,
    fill: Color = flow.glass,
    shadow: Boolean = true,
    edge: Boolean = true,
    border: Color? = null,
): Modifier {
    val shape = RoundedCornerShape(radius)
    var m = this
    if (shadow) m = m.softShadow(radius, flow.shadow)
    m = m.clip(shape).background(fill)
    m =
        when {
            border != null -> m.border(2.dp, border, shape)
            edge -> m.border(1.dp, Brush.verticalGradient(listOf(flow.edgeTop, flow.edgeBottom)), shape)
            else -> m
        }
    return m
}

/** A glass surface; tappable ones give way under the finger. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = AbiTheme.dimens.components.cardCornerRadius,
    onClick: (() -> Unit)? = null,
    fill: Color? = null,
    border: Color? = null,
    shadow: Boolean = true,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val flow = AbiTheme.flow
    val interaction = remember { MutableInteractionSource() }
    var m = modifier
    if (onClick != null) m = m.squish(interaction, 0.975f)
    m = m.glass(flow, radius, fill ?: flow.glass, shadow, border = border)
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
    CompositionLocalProvider(LocalContentColor provides flow.text) {
        Box(m, content = content)
    }
}

/** Round glass button with a symbol, e.g. settings or back. */
@Composable
fun GlassIconButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AbiTheme.flow.text,
    tag: String? = null,
    size: Dp = 44.dp,
) {
    GlassSurface(
        modifier = modifier.size(size).let { if (tag != null) it.testTag(tag) else it },
        radius = size / 2,
        onClick = onClick,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.align(Alignment.Center).size(size * 0.46f),
        )
    }
}

/** A symbol in a softly tinted circle – used for list rows, tiles and plan steps. */
@Composable
fun IconBubble(icon: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val dark = AbiTheme.flow.dark
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (dark) 0.2f else 0.15f)),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = readable(tint), modifier = Modifier.size(size * 0.5f))
    }
}

// ---------------------------------------------------------------- page structure

/** The large title at the top of a main area, with the date or a short status above it. */
@Composable
fun LargeHeader(title: String, caption: String?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val flow = AbiTheme.flow
    val dimens = AbiTheme.dimens
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 2.dp, top = 6.dp, bottom = 2.dp)) {
        Column(Modifier.weight(1f)) {
            if (caption != null) Text(caption, fontSize = dimens.fonts.caption, color = flow.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                title,
                fontSize = dimens.fonts.title * 1.28f,
                lineHeight = dimens.fonts.title * 1.45f,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = flow.text,
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Small bold label above a group of tiles. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = AbiTheme.dimens.fonts.caption * 1.05f,
        fontWeight = FontWeight.Bold,
        color = AbiTheme.flow.subtle,
        modifier = modifier.padding(start = 6.dp, top = 6.dp),
    )
}

/** The status bar stays calm: the content softly fades out under it while scrolling. */
@Composable
fun TopFade(modifier: Modifier = Modifier) {
    val flow = AbiTheme.flow
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(top + 18.dp)
            .background(Brush.verticalGradient(listOf(flow.base.copy(alpha = 0.92f), flow.base.copy(alpha = 0.6f), flow.base.copy(alpha = 0f)))),
    )
}

/** Choice between a few views of one page; the bright thumb glides to the chosen one. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
) {
    val flow = AbiTheme.flow
    val capsule = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(capsule)
            .background(flow.tile)
            .border(1.dp, Brush.verticalGradient(listOf(flow.edgeTop, flow.edgeBottom)), capsule)
            .padding(4.dp),
    ) {
        val count = options.size.coerceAtLeast(1)
        val w = maxWidth / count
        val x by animateDpAsState(w * selected.coerceIn(0, count - 1), spring(dampingRatio = 0.72f, stiffness = 520f), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .softShadow(19.dp, flow.shadow, blur = 8.dp, offsetY = 2.dp)
                .clip(capsule)
                .background(if (flow.dark) Color.White.copy(alpha = 0.14f) else Color.White),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val active = i == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(capsule)
                            .selectable(selected = active, role = Role.Tab, onClick = { if (!active) onSelect(i) })
                            .testTag(tags.getOrNull(i) ?: "segment_$i"),
                ) {
                    Text(
                        label,
                        fontSize = if (count > 3) 13.sp else 14.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) readable(flow.accent) else flow.subtle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tiles and hero

/** The colourful hero card at the top of a page (forecast, points of an evaluation). */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val flow = AbiTheme.flow
    val shape = RoundedCornerShape(28.dp)
    val interaction = remember { MutableInteractionSource() }
    var m =
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.squish(interaction, 0.98f) else it }
            .softShadow(28.dp, flow.accent.copy(alpha = if (flow.dark) 0.18f else 0.24f), blur = 22.dp, offsetY = 10.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(flow.accent.copy(alpha = 0.94f), flow.accent2.copy(alpha = 0.9f))))
            .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(m.padding(18.dp), content = content)
    }
}

/** A small value on the hero card (days, streak, minutes). */
@Composable
fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A white chip on the hero card. */
@Composable
fun HeroChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        maxLines = 2,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.2f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A small counter in a coloured capsule, e.g. "3 fällig" or the number of mistakes. */
@Composable
fun CountChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = readable(color),
        maxLines = 1,
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * A tile for one way to practise: symbol, title, a short line below, optionally a chip with the last points,
 * a red counter for due items, an accent frame for the biggest lever, or the colourful hero look.
 */
@Composable
fun Tile(
    title: String,
    subtitle: String?,
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chip: Pair<String, Color>? = null,
    badge: Int = 0,
    highlight: Boolean = false,
    hero: Boolean = false,
    subtitleColor: Color? = null,
    tag: String? = null,
) {
    val flow = AbiTheme.flow
    val dimens = AbiTheme.dimens
    val shape = RoundedCornerShape(24.dp)
    val interaction = remember { MutableInteractionSource() }
    val base =
        modifier
            .let { if (tag != null) it.testTag(tag) else it }
            .heightIn(min = 112.dp)
            .squish(interaction, 0.95f)
    val surface =
        if (hero) {
            base
                .softShadow(24.dp, flow.accent.copy(alpha = 0.26f), blur = 18.dp, offsetY = 8.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(flow.accent.copy(alpha = 0.94f), flow.accent2.copy(alpha = 0.9f))))
                .border(1.dp, Color.White.copy(alpha = 0.3f), shape)
        } else {
            base.glass(flow, 24.dp, border = if (highlight) flow.accent.copy(alpha = 0.7f) else null)
        }
    val textColor = if (hero) Color.White else flow.text
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            surface
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
                .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box {
                if (hero) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconBubble(icon, tint, size = 36.dp)
                }
                if (badge > 0) {
                    Text(
                        badge.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-4).dp)
                                .clip(RoundedCornerShape(50))
                                .background(red)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (chip != null) {
                if (hero) HeroChip(chip.first) else CountChip(chip.first, chip.second)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            title,
            fontSize = dimens.fonts.body,
            lineHeight = dimens.fonts.body * 1.2f,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = dimens.fonts.caption,
                lineHeight = dimens.fonts.caption * 1.3f,
                color = subtitleColor ?: if (hero) Color.White.copy(alpha = 0.88f) else flow.subtle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- folding rows

/** One glass card holding several rows that open and close – keeps long pages short. */
@Composable
fun FoldGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = modifier.fillMaxWidth(), radius = 24.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
    }
}

/** A row in a [FoldGroup]: symbol, title, optional counter and an arrow that turns when it opens. */
@Composable
fun FoldRow(
    title: String,
    icon: Int,
    iconTint: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    count: String? = null,
    countColor: Color? = null,
    initiallyOpen: Boolean = false,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val flow = AbiTheme.flow
    val dimens = AbiTheme.dimens
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "fold")
    Column(modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(flow.text.copy(alpha = 0.07f)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable { open = !open }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = readable(iconTint), modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = dimens.fonts.body, fontWeight = FontWeight.Medium, color = flow.text)
                if (subtitle != null) Text(subtitle, fontSize = dimens.fonts.caption, color = flow.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                CountChip(count, countColor ?: flow.subtle)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                painterResource(R.drawable.keyboard_arrow_down_24dp),
                contentDescription = if (open) "Zuklappen" else "Aufklappen",
                tint = flow.subtle,
                modifier = Modifier.size(22.dp).rotate(turn),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), content = content)
        }
    }
}
