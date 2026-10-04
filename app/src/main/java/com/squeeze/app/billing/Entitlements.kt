package com.squeeze.app.billing

import android.content.Context
import com.squeeze.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Products this app sells. */
object Products {
    /**
     * Squeeze Pro, the subscription. Configured in Play Console with two auto-renewing base
     * plans, [MONTHLY] and [YEARLY], each carrying a 7-day free-trial offer for new
     * subscribers. Prices come from Play at run time, never from this code.
     */
    const val PRO_SUBSCRIPTION = "squeeze_pro"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"

    /** Earlier one-time product: still honoured for anyone who bought it. */
    const val PRO_LIFETIME = "squeeze_pro_lifetime"
}

/**
 * Whether this user has Pro.
 *
 * Cached in plain preferences on purpose. The threat here is piracy, not disclosure:
 * nothing sensitive is behind this gate, and a user who patches their entitlement has
 * cost a sale, not compromised anyone's data.
 *
 * The cache exists so the app starts instantly and works offline; [BillingManager]
 * reconciles it against the Play Store whenever it can reach it.
 *
 * Debug builds are always Pro: they are not installed from Play, so Play Billing cannot sell
 * them anything, and every feature must stay testable. The paywall still opens from You.
 */
@Singleton
class Entitlements @Inject constructor(
    context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    /** Called by [BillingManager] after reconciling with the Play Store. */
    fun update(pro: Boolean, plan: String?) {
        val next = EntitlementState(pro = pro || BuildConfig.DEBUG, plan = plan, debugUnlocked = BuildConfig.DEBUG && !pro)
        prefs.edit().putBoolean(KEY_PRO, pro).putString(KEY_PLAN, plan).apply()
        _state.value = next
    }

    private fun load(): EntitlementState {
        val paid = prefs.getBoolean(KEY_PRO, false)
        return EntitlementState(
            pro = paid || BuildConfig.DEBUG,
            plan = prefs.getString(KEY_PLAN, null),
            debugUnlocked = BuildConfig.DEBUG && !paid,
        )
    }

    private companion object {
        const val PREFS = "squeeze_entitlements"
        const val KEY_PRO = "pro"
        const val KEY_PLAN = "plan"
    }
}

/**
 * @param plan [Products.PRO_SUBSCRIPTION] or [Products.PRO_LIFETIME]; null when not Pro
 * @param debugUnlocked Pro only because this is a debug build
 */
data class EntitlementState(
    val pro: Boolean = false,
    val plan: String? = null,
    val debugUnlocked: Boolean = false,
)
