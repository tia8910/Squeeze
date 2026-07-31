package com.squeeze.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.squeeze.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives Google Play Billing without a backend.
 *
 * The Play Billing Library communicates with the Play Store app over binder IPC rather
 * than through this app's own network stack, so purchasing works without any server of
 * ours existing. Entitlement is reconciled from [BillingClient.queryPurchasesAsync],
 * which reads the Play Store's local cache and therefore also answers correctly offline —
 * important for an app whose users may keep their phone off the network by choice.
 *
 * Purchases are verified locally by [PurchaseVerifier]; see that class for the honest
 * account of what local verification can and cannot guarantee.
 */
@Singleton
class BillingManager @Inject constructor(
    private val context: Context,
    private val entitlements: Entitlements,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    private var connected = false

    /** Connects and reconciles entitlement. Safe to call repeatedly. */
    fun start() {
        if (connected) {
            scope.launch { reconcile() }
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) scope.launch { reconcile() }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
                // Deliberately not retrying in a loop. The cached entitlement remains valid
                // and the next start() will reconnect; hammering the service would drain
                // battery for a user who is simply offline.
            }
        })
    }

    /**
     * Reads every owned product and updates [Entitlements].
     *
     * Consumables are counted rather than treated as booleans, so a user who buys three
     * training blocks gets three.
     */
    suspend fun reconcile() {
        if (!connected) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return

        val valid = result.purchasesList.filter { it.isTrustworthy() }

        var adFree = false
        var pro = false
        var credits = entitlements.state.value.blockCredits

        for (purchase in valid) {
            for (productId in purchase.products) {
                when (productId) {
                    Products.AD_FREE -> adFree = true
                    Products.PRO_LIFETIME -> pro = true
                    Products.TRAINING_BLOCK -> {
                        // Consumables must be consumed before they can be bought again;
                        // granting the credit and consuming are one atomic step for us.
                        if (consume(purchase)) credits += purchase.quantity
                    }
                }
            }
            acknowledgeIfNeeded(purchase)
        }

        entitlements.update(adFree = adFree, pro = pro, blockCredits = credits)
    }

    /** Launches the purchase flow for [productId]. */
    suspend fun purchase(activity: Activity, productId: String) {
        if (!connected) return

        val details = queryProduct(productId) ?: return
        val offer = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()

        client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(offer))
                .build(),
        )
    }

    suspend fun queryProduct(productId: String): ProductDetails? {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build(),
        )
        return result.productDetailsList?.firstOrNull()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        scope.launch { reconcile() }
    }

    /**
     * A purchase counts only when Play reports it as purchased *and* its signature checks
     * out. Pending purchases are ignored until they settle, so a user is never granted
     * access on a payment that may still fail.
     */
    private fun Purchase.isTrustworthy(): Boolean {
        if (purchaseState != Purchase.PurchaseState.PURCHASED) return false

        // An unconfigured key means a debug build with no Play Console credentials. Trust
        // the Play Store's own response there rather than failing every purchase locally.
        if (!PurchaseVerifier.isConfigured(BuildConfig.PLAY_PUBLIC_KEY)) return true

        return PurchaseVerifier.verify(BuildConfig.PLAY_PUBLIC_KEY, originalJson, signature)
    }

    /** Play refunds any one-time purchase not acknowledged within three days. */
    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        if (purchase.products.any { it in Products.CONSUMABLE }) return

        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        )
    }

    private suspend fun consume(purchase: Purchase): Boolean {
        val result = client.consumePurchase(
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        )
        return result.billingResult.responseCode == BillingClient.BillingResponseCode.OK
    }
}
