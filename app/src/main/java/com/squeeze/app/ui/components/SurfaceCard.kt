package com.squeeze.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The standard content surface.
 *
 * Slightly translucent so the aurora drifting underneath stays perceptible through the
 * layout rather than being hidden by the first card — the motion is what makes the app feel
 * alive, and an opaque card wall would cancel it. Kept opaque enough that body text still
 * meets contrast against any point in the gradient's travel.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = if (tonal) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Transparent placeholder so a card-shaped hole is never left in a layout. */
val TransparentSurface: Color = Color.Transparent
