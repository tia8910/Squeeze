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

        // Restores Pro from the Play Store's cache (works offline) and loads the plans.
        billingManager.start()

        // Reminders are booked with the alarm clock, so they arrive while the app is closed.
        com.squeeze.app.notify.Reminders.schedule(this)
    }
}
