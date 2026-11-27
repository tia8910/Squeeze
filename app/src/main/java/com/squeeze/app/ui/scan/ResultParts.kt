package com.squeeze.app.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import com.squeeze.core.model.BodyFatEstimate
import com.squeeze.core.scan.PhotoIssue

private val HeroGradient = Brush.linearGradient(listOf(Color(0xFF2F8CFF), Color(0xFF0E4BD8), Color(0xFF081C45)))

/**
 * The answer, first and large: body fat today, its category, and how sure it is.
 *
 * The range is drawn, not only written, on a 3 to 40% scale, so a wide interval looks wide.
 * Everything about how the number was reached sits behind "How this was worked out" below it.
 */
@Composable
internal fun ResultHero(
    estimate: BodyFatEstimate,
    category: String?,
    source: String,
) {
    val low = (estimate.percent - estimate.standardErrorPercent)
        .coerceAtLeast(estimate.floorPercent ?: 3.0)
        .coerceAtMost(estimate.percent)
    val high = estimate.percent + estimate.standardErrorPercent

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(HeroGradient)
            .padding(18.dp),
    ) {
        Text(
            "BODY FAT · TODAY",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
        )
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                "%.1f%%".format(estimate.percent),
                color = Color.White,
                fontSize = 52.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            category?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 10.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        // The likely range on a fixed scale.
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(18.dp),
        ) {
            fun at(p: Double) = ((p - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)).coerceIn(0.0, 1.0).toFloat()
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * at(low))
                    .width(maxWidth * (at(high) - at(low)).coerceAtLeast(0.02f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.55f)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = maxWidth * at(estimate.percent) - 2.dp)
                    .size(width = 4.dp, height = 18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                "Likely between %.0f and %.0f%%".format(low, high),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(source, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

private const val SCALE_MIN = 3.0
private const val SCALE_MAX = 40.0

/** What about the photo left areas out, in one amber line, with how to fix it. */
@Composable
internal fun PhotoIssueBanner(issues: Set<PhotoIssue>) {
    if (issues.isEmpty()) return
    val dark = LocalIsDarkTheme.current
    val raised = PhotoIssue.ARM_RAISED in issues || PhotoIssue.ARM_ACROSS_BODY in issues
    val legs = PhotoIssue.LEGS_COVERED in issues
    val title = when {
        raised && legs -> "Arm flexed and loose trousers"
        raised -> if (PhotoIssue.ARM_RAISED in issues) "Arm raised or flexed" else "Arm in front of your body"
        else -> "Loose trousers"
    }
    val parts = buildList {
        if (raised) add("arms, chest")
        if (legs) add("legs")
    }.joinToString(" and ")
    val fix = when {
        raised && legs -> "Stand relaxed in shorts to score them."
        raised -> "Stand relaxed with arms slightly out to score them."
        else -> "Wear shorts to score them."
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (dark) Color(0xFF2A2110) else Color(0xFFFFFBF2))
            .border(1.dp, if (dark) Color(0xFF5C4515) else Color(0xFFF6DEB8), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                "${parts.replaceFirstChar { it.uppercase() }} were left out instead of guessed. $fix",
                style = MaterialTheme.typography.bodySmall,
                color = if (dark) Color(0xFFE8C98F) else Color(0xFF6B4A1A),
            )
        }
    }
}

/** The one next step when the photo held the read back. */
@Composable
internal fun RetakeCard(onRetake: () -> Unit) {
    val dark = LocalIsDarkTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) Color(0xFF2A2110) else Color(0xFFFFFBF2))
            .border(1.dp, if (dark) Color(0xFF5C4515) else Color(0xFFF6DEB8), RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onRetake)
            .padding(16.dp),
    ) {
        Text("FOR A FULL READ", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = Color(0xFFB45309))
        Text("Retake relaxed, in shorts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Phone on a shelf with the timer, arms slightly out. Then every area gets a score and your focus is picked for you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A closed card that opens to show the long explanations, so the screen leads with answers. */
@Composable
internal fun ExpandableSection(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (open) "Hide" else "Show",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}
