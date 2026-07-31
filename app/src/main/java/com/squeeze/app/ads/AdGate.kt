package com.squeeze.app.ads

import com.squeeze.app.billing.Entitlements
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where an ad is about to be shown. Every ad placement in the app must name its surface,
 * so the decision to serve is made by policy rather than by whoever is writing the screen.
 */
enum class AdSurface {
    /** Workout logging and history. Contains training data, no body composition. */
    WORKOUT_LOG,

    /** Exercise browser and reference content. */
    EXERCISE_LIBRARY,

    /** App settings and general navigation. */
    SETTINGS,

    /** Body composition dashboard, trend charts, measurement entry. */
    BODY_COMPOSITION,

    /** Camera capture and anything displaying a captured photo or silhouette. */
    PHOTO_CAPTURE,

    /** Generated training programme detail. */
    PROGRAM,
}

/**
 * Decides whether an ad may be served.
 *
 * Two rules, both hard:
 *
 *  1. **Paying users never see ads.** Removing them is part of what the purchase buys.
 *  2. **Health and photo surfaces never show ads, for anyone.** This is not a taste
 *     judgement. Google Play's Health Apps policy forbids using health data to serve
 *     advertising, and the only structurally safe way to comply is to keep advertising out
 *     of the screens where health data lives, so no ad request can ever be correlated with
 *     a measurement. It also matters for trust: an ad rendered next to a photo of the
 *     user's body reads as though the two are connected, whether or not they are.
 *
 * Callers cannot opt out of these rules — [canShow] is the only way to reach an ad view.
 */
@Singleton
class AdGate @Inject constructor(
    private val entitlements: Entitlements,
) {

    /** Surfaces that may never carry advertising, regardless of entitlement. */
    private val prohibited = setOf(
        AdSurface.BODY_COMPOSITION,
        AdSurface.PHOTO_CAPTURE,
        // Programmes are derived from body composition, so an ad here would be targeted by
        // health data at one remove.
        AdSurface.PROGRAM,
    )

    fun canShow(surface: AdSurface): Boolean {
        if (surface in prohibited) return false
        if (entitlements.isAdFree()) return false
        return true
    }

    /**
     * Whether an interstitial may interrupt right now.
     *
     * Interstitials are held to a stricter standard than banners: they are only acceptable
     * at a natural boundary, never mid-task. Interrupting a logged set loses the user's
     * place in a workout, which is a far larger cost than the impression is worth.
     */
    fun canShowInterstitial(surface: AdSurface, atNaturalBreak: Boolean): Boolean =
        atNaturalBreak && canShow(surface)
}
