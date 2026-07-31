package com.squeeze.app

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.squeeze.app.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SqueezeApplication : Application() {

    @Inject lateinit var billingManager: BillingManager
    @Inject lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        billingManager.start()

        // Ads initialise off the main thread: MobileAds.initialize does disk and network
        // work that measurably delays first frame if run inline.
        applicationScope.launch(Dispatchers.IO) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    // This app is not directed at children, but body-image content warrants
                    // the stricter content rating regardless of who is watching.
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                    .setTagForChildDirectedTreatment(
                        RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE,
                    )
                    .build(),
            )
            MobileAds.initialize(this@SqueezeApplication)
        }
    }
}
