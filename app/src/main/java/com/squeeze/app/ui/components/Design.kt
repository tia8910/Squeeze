package com.squeeze.app.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme

/**
 * The brand sheet's shape and surface language, in one place.
 *
 * Radii are the design's literal values — 24 for cards, 16 for tiles and buttons, 15 for
 * notices and rows. They are close enough to look like rounding errors and are not: the
 * 1px difference between a 16 tile and a 15 row is what keeps a row from reading as a
 * tappable tile, and the values are kept distinct here for that reason.
 */
object SqueezeShape {
    val CardRadius = 24.dp
    val TileRadius = 16.dp
    val RowRadius = 15.dp
    val CardPadding = 20.dp
}

/** Card fill, border and row fills flip with the theme; everything else is shared. */
@Composable
private fun cardFill(): Color = if (LocalIsDarkTheme.current) Brand.DarkCard else Brand.Card

@Composable
private fun lineColour(): Color = if (LocalIsDarkTheme.current) Brand.DarkLine else Brand.Line

@Composable
private fun sunkenFill(): Color = if (LocalIsDarkTheme.current) Brand.DarkSunken else Brand.Sunken

@Composable
private fun rowFill(): Color = if (LocalIsDarkTheme.current) Brand.DarkRowFill else Brand.RowFill

@Composable
private fun iceFill(): Color = if (LocalIsDarkTheme.current) Brand.DarkIce else Brand.Ice

/**
 * The standard content surface: white, hairline border, soft wide shadow.
 *
 * Depth here comes from the border and the shadow's spread rather than from Material
 * elevation tinting, which would wash the card with primary and break the flat white the
 * design depends on.
 */
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = SqueezeShape.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SqueezeShape.CardRadius)

    Column(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = Brand.Navy.copy(alpha = 0.22f),
                spotColor = Brand.Navy.copy(alpha = 0.22f),
            )
            .clip(shape)
            .background(cardFill())
            .border(1.dp, lineColour(), shape)
            .padding(contentPadding),
    ) {
        // Not a Surface either: titles without an explicit colour need the card's own ink.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/**
 * A number over a caption, on the recessed fill.
 *
 * @param tinted uses the ice fill and a blue number, for a tile carrying the headline figure
 *   rather than a supporting count. Used sparingly — once it appears twice in a row it stops
 *   marking anything.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tinted: Boolean = false,
) {
    val shape = RoundedCornerShape(SqueezeShape.TileRadius)

    Column(
        modifier = modifier
            .clip(shape)
            .background(if (tinted) iceFill() else sunkenFill())
            .border(1.dp, lineColour(), shape)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (tinted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (LocalIsDarkTheme.current) Brand.DarkMuted else Brand.Muted,
            maxLines = 1,
        )
    }
}

/** Three tiles across, the design's standard stat strip. */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * The centred pill that states the verdict.
 *
 * Bold blue on ice, centred. It is not a warning and is not styled as one — "no confirmed
 * change yet" is a correct and neutral result, and colouring it amber would tell the user
 * they had done something wrong by having ordinary data.
 */
@Composable
fun NoticePill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SqueezeShape.RowRadius))
            .background(iceFill())
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = if (LocalIsDarkTheme.current) Brand.DarkBlue else Brand.BlueDeep,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The primary action: a blue gradient, white label.
 *
 * Built on [Button] rather than a clickable Box so it keeps the ripple, the disabled state
 * and the accessibility semantics of a real button. The container is transparent and the
 * gradient is painted by a child that fills the bounds, which the button's own shape clips.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // A slow band of light across the button: the one place the eye should go.
    val shine = androidx.compose.animation.core.rememberInfiniteTransition(label = "shine")
    val sweep by shine.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(durationMillis = 3_200, delayMillis = 1_400, easing = LinearOutSlowInEasing),
        ),
        label = "sweep",
    )
    Button(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().height(54.dp).pressScale(source),
        enabled = enabled,
        shape = RoundedCornerShape(SqueezeShape.TileRadius),
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
        ),
        elevation = null,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Brand.IconBlueLight,
                            MaterialTheme.colorScheme.primary,
                            if (LocalIsDarkTheme.current) Brand.Blue else Brand.BlueDeep,
                        ),
                    ),
                )
                .then(
                    if (enabled) {
                        Modifier.drawWithContent {
                            drawContent()
                            val x = size.width * sweep
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                                    start = androidx.compose.ui.geometry.Offset(x - size.height * 1.5f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x + size.height * 1.5f, size.height),
                                ),
                            )
                        }
                    } else {
                        Modifier.graphicsLayer { alpha = 0.45f }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** The secondary action: a tinted blue hairline, blue label, no fill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(SqueezeShape.TileRadius),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (LocalIsDarkTheme.current) {
                Brand.DarkBlue.copy(alpha = 0.55f)
            } else {
                Brand.OutlineBlue
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * A history entry: recessed fill, no border, whole row tappable.
 *
 * @param onClick optional — rows are only tappable where there is somewhere to go.
 */
@Composable
fun BrandRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SqueezeShape.RowRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowFill())
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A section title with the caption that belongs to it.
 *
 * Sections were bare `Text` calls at `titleMedium`, which is a heading in the same voice as
 * everything under it — so a screen of six sections read as six equally important things and
 * the reader had to parse each card to find the one they came for. A heading needs to sit
 * *above* the content in the hierarchy, not beside it.
 *
 * The rule is a small blue eyebrow, a plain-weight title, and an optional line of grey saying
 * what the section is for. The eyebrow is what does the work: it is the only place the accent
 * appears outside data and actions, so it reads as structure rather than as something to tap.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    caption: String? = null,
) {
    val dark = LocalIsDarkTheme.current

    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        eyebrow?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = if (dark) Brand.DarkBlue else Brand.Blue,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (dark) Brand.DarkInk else Brand.Navy,
        )
        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) Brand.DarkMuted else Brand.Muted,
            )
        }
    }
}

/**
 * The one figure a screen exists to show, at the size that says so.
 *
 * Every number in this app was rendered at roughly the same weight, which made a screen a
 * list of equals and left the reader to work out which one was the answer. This is the
 * opposite premise: one figure gets display type, its unit sits small and muted beside it so
 * the digits keep the optical line, and the interval goes underneath rather than beside —
 * because an interval is a qualification of the number, and a qualification set at the same
 * size as its subject reads as a second number.
 *
 * @param band the population category, shown as a chip. Null when there is no published
 *   reference, which is a real state and better than a chip reading "—"
 */
@Composable
fun HeroMetric(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    interval: String? = null,
    band: String? = null,
) {
    val dark = LocalIsDarkTheme.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = if (dark) Brand.DarkMuted else Brand.Muted,
        )

        Row(verticalAlignment = Alignment.Bottom) {
            // A number counts up to itself, so a fresh result reads as computed. Anything
            // that is not a plain number ("--", a range) is shown as it is.
            val numeric = value.replace(",", "").toDoubleOrNull()
            if (numeric != null) {
                AnimatedNumber(
                    value = numeric,
                    decimals = value.substringAfter('.', "").length.takeIf { '.' in value } ?: 0,
                    style = MaterialTheme.typography.displaySmall,
                    color = if (dark) Brand.DarkInk else Brand.Navy,
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (dark) Brand.DarkInk else Brand.Navy,
                )
            }
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dark) Brand.DarkMuted else Brand.Muted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            band?.let { BandChip(it) }
            interval?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dark) Brand.DarkMuted else Brand.Muted,
                )
            }
        }
    }
}

/**
 * The population category a reading falls in.
 *
 * Deliberately one colour for every band. Colouring "Above average" amber would turn a
 * population statement into a verdict on the person, which is the line ReferenceBands draws in
 * its own header and which the UI has no business crossing.
 */
@Composable
fun BandChip(label: String, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (dark) Brand.DarkBlue else Brand.BlueDeep,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(iceFill())
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * Content that arrives rather than appearing.
 *
 * A short rise and fade, once, when a screen's data first resolves. It is not decoration: an
 * app that computes for a moment and then swaps a spinner for a full screen of numbers reads
 * as a page load, and the same content arriving reads as a result. The distance is small on
 * purpose — anything longer becomes something to wait through on the fiftieth scan.
 */
@Composable
fun Arriving(
    visible: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = updateTransition(targetState = visible, label = "arriving")

    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 260, easing = LinearOutSlowInEasing) },
        label = "alpha",
    ) { if (it) 1f else 0f }

    val offset by transition.animateDp(
        transitionSpec = { tween(durationMillis = 320, easing = LinearOutSlowInEasing) },
        label = "offset",
    ) { if (it) 0.dp else 10.dp }

    Box(modifier.graphicsLayer { this.alpha = alpha; translationY = offset.toPx() }) {
        content()
    }
}

/**
 * The premium surface: a deep blue gradient with drifting light, white content. For the one
 * card per screen that says what to do next — used twice on a screen it stops meaning that.
 */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SqueezeShape.CardRadius)
    val glow = androidx.compose.animation.core.rememberInfiniteTransition(label = "hero")
    val drift by glow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(9_000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "drift",
    )
    Column(
        modifier = modifier
            .shadow(16.dp, shape, ambientColor = Brand.Blue.copy(alpha = 0.6f), spotColor = Brand.Blue.copy(alpha = 0.6f))
            .clip(shape)
            .background(Brush.linearGradient(listOf(Brand.IconBlueDeep, Brand.BlueDeep, Brand.Navy)))
            .drawWithContent {
                val c = androidx.compose.ui.geometry.Offset(size.width * (0.2f + 0.7f * drift), size.height * 0.1f)
                val r = size.maxDimension * 0.8f
                drawCircle(Brush.radialGradient(listOf(Brand.IconBlueLight.copy(alpha = 0.55f), Color.Transparent), c, r), r, c)
                drawContent()
            }
            .padding(SqueezeShape.CardPadding),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color.White,
            ) { content() }
        },
    )
}

/** The white-on-blue action inside a [HeroCard]. */
@Composable
fun HeroButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().height(52.dp).pressScale(source),
        shape = RoundedCornerShape(SqueezeShape.TileRadius),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.BlueDeep),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * A pill switcher: options side by side on a recessed track, the chosen one filled in blue.
 * For two to four mutually exclusive views or settings.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    val track = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(track)
            .background(cardFill())
            .border(1.dp, lineColour(), track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val fill by androidx.compose.animation.animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "segment",
            )
            val source = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .pressScale(source)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill)
                    .clickable(interactionSource = source, indication = null, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = when {
                        on -> if (dark) Brand.DarkGround else Color.White
                        dark -> Brand.DarkMuted
                        else -> Brand.Muted
                    },
                )
            }
        }
    }
}

/**
 * The app's filter chip: Material's, but a chosen chip is filled solid blue with a tick, so
 * it reads as chosen at a glance. Material's default — a pale tint and the same label colour —
 * left a ticked food looking almost identical to an unticked one.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon ?: if (selected) {
            {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.height(18.dp),
                )
            }
        } else {
            null
        },
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = lineColour(),
            selectedBorderColor = Color.Transparent,
        ),
    )
}
