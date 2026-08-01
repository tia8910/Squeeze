package com.squeeze.app.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Products this app sells. */
object Products {
    /** One-time purchase: unlocks programme generation. */
    const val PRO_LIFETIME = "squeeze_pro_lifetime"

    /**
     * Consumable: one generated training block.
     *
     * Sold per block rather than per month because a mesocycle is the unit lifters
     * already think in, and because a consumable is the simplest Play Billing product to
     * verify with no server — there is no renewal, grace period or account hold state to
     * reconcile, and [BillingManager] can settle entitlement entirely from the Play
     * Store's local cache.
     */
    const val TRAINING_BLOCK = "squeeze_training_block"

    val ONE_TIME = listOf(PRO_LIFETIME)
    val CONSUMABLE = listOf(TRAINING_BLOCK)
}

/**
 * What the user currently owns.
 *
 * Cached in plain preferences on purpose. The threat here is piracy, not disclosure:
 * nothing sensitive is behind this gate, and a user who patches their entitlement has
 * cost a sale, not compromised anyone's data. Spending engineering effort hardening it
 * would be effort not spent on measurement accuracy, which is what people actually pay for.
 *
 * The cache exists so the app works offline and starts instantly; [BillingManager]
 * reconciles it against the Play Store whenever it can reach it.
 */
@Singleton
class Entitlements @Inject constructor(
    private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    fun canGenerateProgram(): Boolean = _state.value.let { it.pro || it.blockCredits > 0 }

    /** Called by [BillingManager] after reconciling with the Play Store. */
    fun update(pro: Boolean, blockCredits: Int) {
        val next = EntitlementState(pro = pro, blockCredits = blockCredits)
        prefs.edit()
            .putBoolean(KEY_PRO, next.pro)
            .putInt(KEY_CREDITS, next.blockCredits)
            .apply()
        _state.value = next
    }

    /** Spends one block credit when a programme is generated. Pro users are never charged. */
    fun consumeBlockCredit(): Boolean {
        val current = _state.value
        if (current.pro) return true
        if (current.blockCredits <= 0) return false
        update(current.pro, current.blockCredits - 1)
        return true
    }

    private fun load() = EntitlementState(
        pro = prefs.getBoolean(KEY_PRO, false),
        blockCredits = prefs.getInt(KEY_CREDITS, 0),
    )

    private companion object {
        const val PREFS = "squeeze_entitlements"
        const val KEY_PRO = "pro"
        const val KEY_CREDITS = "block_credits"
    }
}

data class EntitlementState(
    val pro: Boolean = false,
    val blockCredits: Int = 0,
)
