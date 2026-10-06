package com.squeeze.app.billing

import android.content.Context
import com.squeeze.app.BuildConfig
import java.security.MessageDigest
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

    /** Pro unlocked with the access code given to Google Play's app reviewers. */
    const val REVIEW_ACCESS = "review_access"
}

/**
 * The access code for Google Play's app reviewers, who cannot buy or start a free trial.
 *
 * Only the SHA-256 of the code is in this public repository; the code itself is given to
 * Play in the App access declaration. It is 16 random characters (80 bits), so it cannot be
 * guessed from the hash. Like the entitlement cache, it guards a sale, not anyone's data.
 */
object ReviewAccess {
    private const val CODE_SHA256 = "caac55522065838e4672a0526a3c19fa2c99fc08d15dcb84801b1793c0ba9a3b"

    /** Spaces, dashes and case are ignored, so the code can be typed however it was copied. */
    fun matches(code: String): Boolean {
        val normalised = code.uppercase().filter(Char::isLetterOrDigit)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalised.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } == CODE_SHA256
    }
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
        prefs.edit().putBoolean(KEY_PRO, pro).putString(KEY_PLAN, plan).apply()
        _state.value = load()
    }

    /** Unlocks Pro on this device when [code] is the reviewer access code. */
    fun redeem(code: String): Boolean {
        if (!ReviewAccess.matches(code)) return false
        prefs.edit().putBoolean(KEY_REVIEW, true).apply()
        _state.value = load()
        return true
    }

    private fun load(): EntitlementState {
        val paid = prefs.getBoolean(KEY_PRO, false)
        val review = prefs.getBoolean(KEY_REVIEW, false)
        return EntitlementState(
            pro = paid || review || BuildConfig.DEBUG || !BuildConfig.PRO_ON_SALE,
            plan = if (paid) prefs.getString(KEY_PLAN, null) else if (review) Products.REVIEW_ACCESS else null,
            debugUnlocked = BuildConfig.DEBUG && !paid && !review,
            onSale = BuildConfig.PRO_ON_SALE,
        )
    }

    private companion object {
        const val PREFS = "squeeze_entitlements"
        const val KEY_PRO = "pro"
        const val KEY_PLAN = "plan"
        const val KEY_REVIEW = "review_access"
    }
}

/**
 * @param plan [Products.PRO_SUBSCRIPTION], [Products.PRO_LIFETIME] or [Products.REVIEW_ACCESS];
 *   null when not Pro
 * @param debugUnlocked Pro only because this is a debug build
 * @param onSale whether Pro is sold yet; while false every feature is free and nothing
 *   about Pro is shown
 */
data class EntitlementState(
    val pro: Boolean = false,
    val plan: String? = null,
    val debugUnlocked: Boolean = false,
    val onSale: Boolean = true,
)
