/*
AbiCoach – Vorbereitung auf die mündliche Abiturprüfung Englisch (NRW)
Die schwebende Glas-Leiste unten, gebaut nach der „glass navigation bar“ aus Morphe (YouTube):
eine durchscheinende Kapsel mit Unschärfe, Glanz, Lichtkante und weichem Schatten,
in der die Markierung zum gewählten Bereich gleitet.
Lizenz: GNU GPL v3 oder neuer.
*/

package de.abicoach.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.Flow
import de.abicoach.ui.theme.readable
import de.abicoach.ui.theme.red
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Height of the floating bar. */
val DockHeight: Dp = 64.dp

/** Gap between the bar and the lower screen edge (above the system navigation). */
val DockGap: Dp = 12.dp

/** Real blur behind the bar needs Android 12; older phones get frosted glass without blur. */
private val blurAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Remembers what the bar blurs; null on phones without blur. */
@Composable
fun rememberDockBlur(): HazeState? {
    if (!blurAvailable) return null
    return rememberHazeState()
}

/** Marks the content that scrolls behind the bar, so the bar can blur it. */
fun Modifier.dockSource(state: HazeState?): Modifier = if (state != null) hazeSource(state = state) else this

/** Space a scrolling list needs at its end so its last card can scroll above the bar. */
@Composable
fun dockSpace(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + DockGap + DockHeight + 18.dp

private fun Modifier.dockBackdrop(state: HazeState?, flow: Flow): Modifier =
    if (state != null) {
        hazeEffect(
            state = state,
            style =
                HazeStyle(
                    backgroundColor = flow.base,
                    tints = listOf(HazeTint(flow.solid.copy(alpha = if (flow.dark) 0.42f else 0.5f))),
                    blurRadius = 24.dp,
                    noiseFactor = 0.04f,
                ),
        )
    } else {
        background(flow.solid.copy(alpha = 0.94f))
    }

/**
 * The floating glass bar of the four areas. The highlight behind the chosen area flows to the new one and
 * stretches a little on the way, like a drop of liquid.
 */
@Composable
fun GlassDock(
    current: Tab,
    onSelect: (Tab) -> Unit,
    blur: HazeState?,
    modifier: Modifier = Modifier,
    badge: (Tab) -> Boolean = { false },
) {
    val flow = AbiTheme.flow
    val capsule = RoundedCornerShape(50)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = bottom + DockGap)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(DockHeight)
                .softShadow(DockHeight / 2, flow.shadow.copy(alpha = (flow.shadow.alpha * 1.7f).coerceAtMost(0.6f)), blur = 22.dp, offsetY = 10.dp)
                .clip(capsule)
                .dockBackdrop(blur, flow)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (flow.dark) 0.10f else 0.42f), Color.White.copy(alpha = 0f)),
                    ),
                )
                .border(1.dp, Brush.verticalGradient(listOf(flow.edgeTop, flow.edgeBottom)), capsule)
                .padding(6.dp)
                .testTag("dock"),
        ) {
            val tabs = Tab.entries
            val itemWidth = maxWidth / tabs.size
            val target = itemWidth * tabs.indexOf(current).coerceAtLeast(0)
            // The front edge runs ahead, the back edge follows: the highlight stretches while it moves.
            val lead by animateDpAsState(target, spring(dampingRatio = 0.78f, stiffness = 700f), label = "dockLead")
            val trail by animateDpAsState(target, spring(dampingRatio = 0.82f, stiffness = 240f), label = "dockTrail")
            val left = min(lead, trail)
            val right = max(lead, trail) + itemWidth
            Box(
                Modifier
                    .offset(x = left)
                    .width(right - left)
                    .fillMaxHeight()
                    .clip(capsule)
                    .background(flow.accent.copy(alpha = if (flow.dark) 0.24f else 0.14f)),
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    DockItem(
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
}

@Composable
private fun DockItem(tab: Tab, selected: Boolean, showBadge: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val flow = AbiTheme.flow
    val color by animateColorAsState(if (selected) readable(flow.accent) else flow.subtle, tween(200), label = "dockColor")
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
            modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .squish(interaction, 0.88f)
                .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
                .testTag(tab.tag),
    ) {
        Box {
            Icon(painter = painterResource(tab.icon), contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
            if (showBadge) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(8.dp).clip(CircleShape).background(red))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}
