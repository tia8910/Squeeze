# Squeeze

An Android body composition and training app that keeps every measurement on the device
that took it.

## What it does

Tracks body composition from tape measurements, skinfolds or reference scans, separates
real change from measurement noise, and generates training blocks that adapt to what the
composition trend actually shows.

## What it claims about accuracy

Body fat cannot be measured accurately by a phone, and this app does not pretend otherwise.

| Method | Accuracy vs DEXA | Repeatability |
|---|---|---|
| Tape (Navy / Hodgdon-Beckett) | ±3.5 pts | ±0.5 pts |
| Skinfolds (Jackson-Pollock 3-site) | ±3.5 pts | ±0.8 pts |
| Photo silhouette | ±4.0 pts | ±0.6 pts |
| BMI estimate (fallback only) | ±4.5 pts | ±0.2 pts |
| DEXA / BodPod (reference) | ±1.5 pts | ±1.0 pts |

The distinction between those two columns is the entire design.

**Accuracy** is dominated by a systematic, person-specific offset — an equation fitted to a
population sits some fixed distance from the truth for any individual. **Repeatability** is
random scatter between two measurements of an unchanged body.

Because the offset is roughly constant, it cancels out when you compare a person against
themselves. So:

- **For absolute numbers**, accuracy governs — and the app shows a confidence interval
  next to every estimate rather than a bare figure.
- **For change over time**, only repeatability matters — and repeatability is five to seven
  times better than accuracy for every method here.

That gap is what makes a phone useful for tracking a cut even though it cannot tell you
your body fat to the point. Conflating the two is the standard mistake in this category:
feeding accuracy into a trend filter makes a real 0.3 %/week cut statistically invisible
for three months, even though the user can see it in the mirror.

A single DEXA or BodPod result removes most of the systematic offset —
`PersonalCalibration` fits a personal correction and collapses absolute error toward the
reference method's own precision.

## Architecture

```
core/    Pure Kotlin/JVM. No Android dependencies.
         Body fat equations, personal calibration, Kalman trend filter,
         repeatability scoring, programme generation, composition feedback,
         and the body-scan geometry.
         98 unit tests, runnable on any machine without an SDK or emulator.

app/     Android. Compose UI, encrypted Room storage, on-device vision, billing.
```

Keeping the maths in a plain JVM module is what makes it testable. Everything that decides
a number a user might act on lives in `core/` and is covered by tests.

### Data protection

- **SQLCipher whole-file encryption.** The metadata is as revealing as the values — that
  someone measured daily for six months says plenty without the numbers.
- **Envelope-encrypted key.** A random passphrase, wrapped by a hardware-backed Android
  Keystore AES-GCM key. Only the wrapped blob is stored.
  (`androidx.security:security-crypto` is deprecated and deliberately not used.)
- **`FLAG_SECURE` available as a one-tap setting** (Settings → Block screenshots): blocks
  screenshots, screen recording and the recent-apps thumbnail. Off by default, because the
  flag is all-or-nothing — leaving it on would stop users capturing their own progress and
  make store listing screenshots impossible to produce. Screens rendering a captured body
  photo should set it unconditionally regardless of the preference.
- **No `INTERNET` permission.** The app cannot open a socket, so no photograph and no
  measurement can leave the device even if a bug or a dependency tried to send one. This is
  enforced by the OS rather than promised, and any user can verify it in the permission
  list. Body scanning uses models packaged in the APK; Play Billing reaches the Play Store
  over binder IPC, not this process's network stack.
- **Biometric gate** on launch and on every return to the foreground.
- **Backup and device transfer disabled.** Cloud backup would put the database on someone
  else's server, which contradicts the guarantee. The user's route to a backup is the
  app's own passphrase-encrypted export.

### Monetisation

- **Pro lifetime** — one-time purchase, unlocks programme generation.
- **Training block** — consumable, one generated mesocycle.

Blocks are sold as consumables rather than as a subscription because a mesocycle is the
unit lifters already think in, and because a consumable is the simplest Play Billing
product to settle with no server: no renewals, grace periods or account holds to reconcile.
Entitlement resolves from the Play Store's local cache, so it works offline.

Purchases are verified on-device against the Play Console key. This does not survive a
patched APK — see `PurchaseVerifier` for why that exposure is accepted rather than fought.

## Building

```bash
./gradlew :core:test          # measurement and programming logic, no SDK needed
./gradlew :app:assembleDebug  # requires the Android SDK
```

A fresh clone builds and runs without any credentials. An absent Play licensing key makes
`PurchaseVerifier` defer to the Play Store's own response, which is the correct behaviour
for a development build with no Play Console behind it.

### Local release builds

Create `keystore.properties` at the repository root (gitignored):

```properties
KEYSTORE_FILE=/absolute/path/to/release.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
PLAY_PUBLIC_KEY=...
```

Without it, `:app:assembleRelease` still succeeds and produces an **unsigned** APK, which
is useful for checking that R8 and resource shrinking behave.

## Releasing

`.github/workflows/release.yml` builds a signed AAB for Play and a signed APK for direct
install. Push a version tag:

```bash
git tag v0.2.0 && git push origin v0.2.0
```

That runs the `:core` tests, builds both artifacts, **verifies the signatures**
(`apksigner` for the APK, `jarsigner` for the AAB — an unsigned artifact is otherwise a
silent build success that only fails at Play upload), and opens a **draft** GitHub release
with them attached.

`workflow_dispatch` runs the same build as a dry run without publishing anything.

### Required repository secrets

| Secret | Notes |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | |
| `KEY_ALIAS` | |
| `KEY_PASSWORD` | |
| `PLAY_PUBLIC_KEY` | Play Console → Monetisation setup → licensing key |

`versionCode` comes from the workflow run number, which is monotonic — re-running a tag
produces a higher code rather than one Play will reject as a duplicate. `versionName`
comes from the tag.

`mapping.txt` is kept as a build artifact but deliberately **not** attached to the release:
it belongs in Play Console for deobfuscating crash reports, not on a public page where it
hands anyone a map of the obfuscated build.

## Status

Working: profile setup, manual measurement entry, automatic body scanning from the camera
or uploaded photos, the composition dashboard with trend and confidence bands, and training
block generation that adapts to the composition trend.

Not built yet: workout logging against a generated block, measurement history browsing, and
encrypted export/import.

## Licence

GPL-3.0. The source is public so the privacy claims can be audited rather than trusted.
