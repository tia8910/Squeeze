package com.squeeze.app.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.squeeze.app.billing.BillingManager
import com.squeeze.app.billing.EntitlementState
import com.squeeze.app.billing.Entitlements
import com.squeeze.app.billing.PlanOption
import com.squeeze.app.billing.Products
import com.squeeze.app.ui.components.AuroraBackground
import com.squeeze.app.ui.components.BrandCard
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.entrance
import com.squeeze.app.ui.theme.Brand
import com.squeeze.app.ui.theme.LocalIsDarkTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ProViewModel @Inject constructor(
    private val billing: BillingManager,
    private val entitlements: Entitlements,
) : ViewModel() {
    val state: StateFlow<EntitlementState> = entitlements.state
    val plans: StateFlow<List<PlanOption>> = billing.plans

    /** Reconnects to Play: refreshes prices and restores any subscription on this account. */
    fun refresh() = billing.start()

    fun subscribe(activity: Activity, plan: PlanOption) = billing.subscribe(activity, plan)

    fun redeem(code: String): Boolean = entitlements.redeem(code)
}

/** What Pro adds, in the order people care about it. */
private val FEATURES = listOf(
    Triple(Icons.Rounded.FitnessCenter, "Your training week", "Built for your sports, goal and weak points"),
    Triple(Icons.Rounded.TrendingUp, "Set by set coaching", "Logging with progressive overload advice"),
    Triple(Icons.Rounded.Restaurant, "Nutrition and meals", "Macros, micronutrients and meals from foods you like"),
    Triple(Icons.Rounded.Workspaces, "Machine scan", "Photograph a machine, see how to use it"),
    Triple(Icons.Rounded.Lightbulb, "Daily coach tips", "For your goal and training time"),
    Triple(Icons.Rounded.CloudDone, "Google Drive backup", "Automatic, to your own Drive"),
)

/**
 * The Pro screen: what it adds, the two plans with the trial, and the terms in plain words.
 * Prices always come from Google Play, in the user's currency; the fallbacks only show while
 * Play is answering.
 */
@Composable
fun PaywallScreen(onClose: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    var chosen by rememberSaveable { mutableStateOf(Products.YEARLY) }
    var askCode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    val yearly = plans.firstOrNull { it.periodMonths == 12 }
    val monthly = plans.firstOrNull { it.periodMonths == 1 }
    val saving = if (yearly != null && monthly != null && monthly.priceMicros > 0) {
        (100 - yearly.priceMicros * 100 / (monthly.priceMicros * 12)).toInt().takeIf { it > 0 }
    } else {
        17
    }
    val selected = if (chosen == Products.YEARLY) yearly else monthly
    val trialDays = selected?.trialDays ?: 7
    val dark = LocalIsDarkTheme.current
    val muted = if (dark) Brand.DarkMuted else Brand.Muted

    AuroraBackground(Modifier.fillMaxSize(), intense = true) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.entrance(0)) {
                Text("SQUEEZE PRO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (state.pro && !state.debugUnlocked) "You're Pro" else "Turn your scan into a plan",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "The body scan stays free. Pro adds the coach around it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            BrandCard(Modifier.fillMaxWidth().entrance(1)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FEATURES.forEach { (icon, title, detail) -> FeatureRow(icon, title, detail) }
                }
            }

            if (state.plan == Products.REVIEW_ACCESS) {
                Text("Unlocked with an access code.", style = MaterialTheme.typography.bodyMedium, color = muted)
                PrimaryButton(text = "Done", onClick = onClose)
                return@Column
            }

            if (state.pro && !state.debugUnlocked) {
                PrimaryButton(text = "Manage subscription", onClick = { openSubscriptions(context) })
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                return@Column
            }

            PlanCard(
                title = "Yearly",
                price = yearly?.price ?: "$50.00",
                per = "a year",
                note = yearly?.let { "about ${perMonth(it)} a month" } ?: "about $4.17 a month",
                badge = saving?.let { "Save $it%" },
                selected = chosen == Products.YEARLY,
                onClick = { chosen = Products.YEARLY },
                modifier = Modifier.entrance(2),
            )
            PlanCard(
                title = "Monthly",
                price = monthly?.price ?: "$5.00",
                per = "a month",
                note = "cancel anytime",
                badge = null,
                selected = chosen == Products.MONTHLY,
                onClick = { chosen = Products.MONTHLY },
                modifier = Modifier.entrance(3),
            )

            PrimaryButton(
                text = when {
                    selected == null -> "Connecting to Google Play…"
                    trialDays > 0 -> "Start $trialDays-day free trial"
                    else -> "Subscribe"
                },
                enabled = selected != null && activity != null,
                onClick = { if (selected != null && activity != null) viewModel.subscribe(activity, selected) },
            )
            Text(
                if (trialDays > 0) {
                    "Free for $trialDays days, then ${selected?.price ?: if (chosen == Products.YEARLY) "$50.00" else "$5.00"} " +
                        "${if (chosen == Products.YEARLY) "a year" else "a month"}. Cancel anytime in Google Play before the " +
                        "trial ends and you won't be charged."
                } else {
                    "Renews automatically. Cancel anytime in Google Play."
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.debugUnlocked) {
                Text(
                    "Test build: Pro is unlocked so every feature can be tried. Purchases work in the Play Store version.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = viewModel::refresh) { Text("Restore purchase") }
                TextButton(onClick = onClose) { Text("Not now") }
            }
            TextButton(onClick = { askCode = true }, modifier = Modifier.fillMaxWidth()) { Text("Have an access code?") }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (askCode) AccessCodeDialog(onRedeem = viewModel::redeem, onDismiss = { askCode = false })
}

/** Where Google Play's reviewers enter the access code from the App access declaration. */
@Composable
private fun AccessCodeDialog(onRedeem: (String) -> Boolean, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Access code") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it; wrong = false },
                singleLine = true,
                isError = wrong,
                supportingText = { if (wrong) Text("That code isn't valid") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (onRedeem(code)) onDismiss() else wrong = true }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    per: String,
    note: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val dark = LocalIsDarkTheme.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) Brand.DarkCard else Brand.Card)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                badge?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.linearGradient(listOf(Brand.IconBlueLight, Brand.BlueDeep)))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(price, style = MaterialTheme.typography.titleMedium)
            Text(per, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Shown in place of a Pro screen for someone without Pro: what is behind it and the way in.
 */
@Composable
fun ProLocked(title: String, detail: String, onUpgrade: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Brand.IconBlueLight, Brand.IconBlueDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
        PrimaryButton(text = "Try Pro free for 7 days", onClick = onUpgrade)
        Text(
            "Your body scan and history stay free.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private fun perMonth(plan: PlanOption): String {
    val monthly = plan.priceMicros / 12 / 10_000 / 100.0
    val symbol = plan.price.takeWhile { !it.isDigit() }.ifBlank { "" }
    return if (symbol.isNotBlank()) "$symbol${"%.2f".format(monthly)}" else "%.2f".format(monthly)
}

private fun openSubscriptions(context: Context) {
    val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Products.PRO_SUBSCRIPTION}&package=${context.packageName}")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
