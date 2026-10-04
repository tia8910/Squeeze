package com.squeeze.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.squeeze.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One way to pay for Pro, as Play describes it.
 *
 * @param price the recurring price after any trial, formatted by Play in the user's currency
 * @param trialDays length of the free trial this user is eligible for; 0 when none (Play only
 *   returns the trial offer to people who have not had one)
 */
data class PlanOption(
    val basePlanId: String,
    val price: String,
    val priceMicros: Long,
    val periodMonths: Int,
    val trialDays: Int,
    val offerToken: String,
)

/**
 * Drives Google Play Billing without a backend.
 *
 * The Play Billing Library talks to the Play Store app over binder IPC, so no server of ours
 * is involved. Entitlement is reconciled from [BillingClient.queryPurchasesAsync], which
 * reads the Play Store's own cache: an active subscription (including one in its free trial
 * or grace period) is returned, a cancelled one stops being returned when it lapses.
 *
 * Purchases are verified locally by [PurchaseVerifier]; see that class for the honest
 * account of what local verification can and cannot guarantee.
 */
@Singleton
class BillingManager @Inject constructor(
    context: Context,
    private val entitlements: Entitlements,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build(),
        )
        .build()

    private var connected = false
    private var details: ProductDetails? = null

    private val _plans = MutableStateFlow<List<PlanOption>>(emptyList())

    /** The plans on offer, once Play has answered; empty before that or when offline. */
    val plans: StateFlow<List<PlanOption>> = _plans.asStateFlow()

    /** Connects and reconciles entitlement. Safe to call repeatedly. */
    fun start() {
        if (connected) {
            scope.launch { reconcile(); loadPlans() }
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) scope.launch { reconcile(); loadPlans() }
            }

            override fun onBillingServiceDisconnected() {
                // Not retried in a loop: the cached entitlement stays valid and the next
                // start() reconnects, without draining battery for someone offline.
                connected = false
            }
        })
    }

    /** Reads every owned product and subscription and updates [Entitlements]. */
    suspend fun reconcile() {
        if (!connected) return

        val owned = buildList {
            for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
                val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build())
                if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    addAll(result.purchasesList.filter { it.isTrustworthy() })
                }
            }
        }

        var plan: String? = null
        for (purchase in owned) {
            when {
                Products.PRO_SUBSCRIPTION in purchase.products -> plan = plan ?: Products.PRO_SUBSCRIPTION
                Products.PRO_LIFETIME in purchase.products -> plan = Products.PRO_LIFETIME
            }
            acknowledgeIfNeeded(purchase)
        }
        entitlements.update(pro = plan != null, plan = plan)
    }

    /** Fetches the subscription's plans and trial offers from Play. */
    suspend fun loadPlans() {
        if (!connected) return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(Products.PRO_SUBSCRIPTION)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        )
        val found = result.productDetailsList?.firstOrNull() ?: return
        details = found
        _plans.value = found.subscriptionOfferDetails.orEmpty()
            .groupBy { it.basePlanId }
            .mapNotNull { (basePlan, offers) ->
                // Prefer an offer with a free phase (the trial) when Play offers one to
                // this user; otherwise the plain base plan.
                val chosen = offers.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }
                    ?: offers.firstOrNull { it.offerId == null }
                    ?: offers.first()
                val phases = chosen.pricingPhases.pricingPhaseList
                val recurring = phases.last()
                val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
                PlanOption(
                    basePlanId = basePlan,
                    price = recurring.formattedPrice,
                    priceMicros = recurring.priceAmountMicros,
                    periodMonths = months(recurring.billingPeriod),
                    trialDays = trial?.let { days(it.billingPeriod) } ?: 0,
                    offerToken = chosen.offerToken,
                )
            }
            .sortedByDescending { it.periodMonths }
    }

    /** Opens Google Play's purchase sheet for [plan]. */
    fun subscribe(activity: Activity, plan: PlanOption) {
        val product = details ?: return
        client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(product)
                            .setOfferToken(plan.offerToken)
                            .build(),
                    ),
                )
                .build(),
        )
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        scope.launch { reconcile() }
    }

    /**
     * A purchase counts only when Play reports it as purchased *and* its signature checks
     * out. Pending purchases are ignored until they settle.
     */
    private fun Purchase.isTrustworthy(): Boolean {
        if (purchaseState != Purchase.PurchaseState.PURCHASED) return false
        // No key configured (a build with no Play Console behind it): trust Play's answer.
        if (!PurchaseVerifier.isConfigured(BuildConfig.PLAY_PUBLIC_KEY)) return true
        return PurchaseVerifier.verify(BuildConfig.PLAY_PUBLIC_KEY, originalJson, signature)
    }

    /** Play refunds a purchase or first subscription payment not acknowledged in 3 days. */
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        )
    }

    private companion object {
        /** ISO 8601 periods as Play sends them: P1M, P1Y, P1W, P7D. */
        fun months(period: String): Int = when {
            period.endsWith("Y") -> 12 * (period.filter(Char::isDigit).toIntOrNull() ?: 1)
            period.endsWith("M") -> period.filter(Char::isDigit).toIntOrNull() ?: 1
            else -> 0
        }

        fun days(period: String): Int {
            val n = period.filter(Char::isDigit).toIntOrNull() ?: 0
            return when {
                period.endsWith("W") -> n * 7
                period.endsWith("D") -> n
                period.endsWith("M") -> n * 30
                else -> 0
            }
        }
    }
}
