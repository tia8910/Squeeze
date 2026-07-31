package com.squeeze.app.ui.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.squeeze.app.BuildConfig
import com.squeeze.app.ads.AdGate
import com.squeeze.app.ads.AdSurface

/**
 * The only way to render a banner in this app.
 *
 * Placement is decided by [AdGate], not by the calling screen, so a health surface cannot
 * acquire an ad by mistake during a refactor. When the gate says no, this composable emits
 * nothing at all — no reserved space, no placeholder — because a paying user should see a
 * layout with no hole in it.
 */
@Composable
fun AdBanner(
    surface: AdSurface,
    adGate: AdGate,
    modifier: Modifier = Modifier,
) {
    if (!adGate.canShow(surface)) return

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.AD_UNIT_BANNER
                loadAd(
                    AdRequest.Builder()
                        // No custom targeting is attached. Nothing derived from the user's
                        // measurements, weight, goal or training history is ever passed to
                        // the ad request: Play's Health Apps policy forbids advertising on
                        // health data, and contextual serving is the only compliant option.
                        .build(),
                )
            }
        },
    )
}
