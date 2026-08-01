package com.squeeze.app

import android.app.Application
import com.squeeze.app.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SqueezeApplication : Application() {

    @Inject lateinit var billingManager: BillingManager

    override fun onCreate() {
        super.onCreate()

        // Billing reaches the Play Store over binder IPC rather than this process's network
        // stack, which is why it still works with no INTERNET permission declared.
        billingManager.start()
    }
}
