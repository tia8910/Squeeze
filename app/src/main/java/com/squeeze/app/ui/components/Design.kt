package com.squeeze.app.ui.components

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
object Squeeze {
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
    contentPadding: Dp = Squeeze.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Squeeze.CardRadius)

    Column(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Brand.Navy.copy(alpha = 0.5f),
                spotColor = Brand.Navy.copy(alpha = 0.5f),
            )
            .clip(shape)
            .background(cardFill())
            .border(1.dp, lineColour(), shape)
            .padding(contentPadding),
        content = content,
    )
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
    val shape = RoundedCornerShape(Squeeze.TileRadius)

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
            .clip(RoundedCornerShape(Squeeze.RowRadius))
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
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(Squeeze.TileRadius),
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
                            MaterialTheme.colorScheme.primary,
                            if (LocalIsDarkTheme.current) Brand.Blue else Brand.BlueDeep,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
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
        shape = RoundedCornerShape(Squeeze.TileRadius),
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
    val shape = RoundedCornerShape(Squeeze.RowRadius)

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
