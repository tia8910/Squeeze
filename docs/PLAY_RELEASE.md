# Releasing Squeeze.fit on Google Play

Everything the Play Console asks for, in the order it asks. The app side is done: target SDK
36, signed AAB from the Release workflow, R8 rules checked on every CI run, privacy policy
live on the website.

## 1. One time setup

### Upload key (no computer needed)

The Release workflow creates the upload key itself. You add one secret from your phone:

1. In a password manager, generate a random password of 32 or more characters and save it
   there as "Squeeze upload key". **If it is lost, the key is lost** (Play can reset an
   upload key, but it takes a support request).
2. github.com/tia8910/Squeeze › Settings › Secrets and variables › Actions › New repository
   secret: name `SIGNING_PASSPHRASE`, value that password.
3. Actions › **Release** › Run workflow › choose this branch › Run.

The first run creates the key, encrypts it with the passphrase and commits only the encrypted
file (`signing/upload.jks.enc`). Every later run decrypts it, so all releases share one key.
The run's "Upload key" step prints the key's SHA-1 for the Google Cloud Android client.

Later, once the app exists in Play Console, add a second secret `PLAY_PUBLIC_KEY` (Play
Console › Monetise › Monetisation setup › Licensing) so the app can check purchases itself.

(A key made on a computer still works: set `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` instead, and those take priority.)

### Build the release

Run the **Release** workflow from the Actions tab (or push a tag such as `v1.0.0`). It produces `squeeze-1.0.0.aab` (upload this) plus a signed APK and the R8 mapping file
in the `squeeze-release` artifact.

### Play App Signing and Google sign in

Upload the first AAB with Play App Signing on (the default). Then:

1. Play Console › Test and release › App integrity › copy the **App signing key** SHA-1
   (and the upload key SHA-1).
2. Google Cloud, project **Squeeze** (71286678065) › Clients › Create client › Android:
   package `fit.squeeze.app`, the App signing SHA-1. Add a second one with the upload key
   SHA-1 if you will install release builds outside Play.
3. Google Auth Platform › Audience: **Publish app** (move out of Testing) so any Google
   account can sign in. `drive.appdata` is a non sensitive scope, so no verification review
   is needed.

## 2. Store listing

**App name:** Squeeze.fit

**Short description (80 max):**
Body scan, training and nutrition plans. Private: photos never leave your phone.

**Full description:**

Squeeze.fit reads your body from one photo, builds your training week and your meals, and
coaches you set by set. Everything runs on your phone.

BODY SCAN
• Body fat and lean mass from a front photo and your weight
• Physique analysis by muscle group: what is strong and what to bring up for your goal
• Covered areas are left out, so shorts never count against your legs
• A trend that separates real change from day to day noise

TRAINING
• Pick your sports: gym, calisthenics, running, Pilates, yoga and more, or combine them
• A week built for your goal, experience and weak points
• Log every set with progressive overload advice for the next one
• Photograph a gym machine to see how to use it and what to do on it

NUTRITION
• Calories and macros from your body and your goal
• Meals built from the foods you like, timed around your training
• 11 micronutrients tracked so nothing runs short

PRIVATE BY DESIGN
• Photos are analysed on your phone and never uploaded
• Everything is stored encrypted, behind your fingerprint or face lock
• Optional backup to a hidden folder in your own Google Drive
• No ads. No analytics. No tracking.

**Category:** Health & Fitness
**Contact email:** your support address
**Privacy policy:** `https://<your website>/privacy`

**Graphics:** icon 512×512, feature graphic 1024×500, at least 2 phone screenshots
(the redesign mockup screens are a good guide for which to take).

## 3. App content declarations

**Privacy policy:** the URL above.

**Ads:** No.

**App access (first release, Pro not on sale):** choose *All functionality is available
without special access*. Everything is free in that build.

**App access (once Pro is on sale):** Choose *Yes, some parts are restricted*. Play reviewers cannot buy or start
free trials, so the app has a reviewer access code (only its hash is in this repository; the
code itself is kept private). Add one instruction set, name "Squeeze Pro", username empty,
password the access code, and this text:

> No account or login is needed. The body scan is free. To unlock all Pro features
> (Training, Nutrition, Log, Machine scan, Drive backup) without paying: open the You tab,
> tap Squeeze Pro, tap "Have an access code?" at the bottom, enter the code in the password
> field and tap Unlock. Google sign in is optional and only used for backup to the user's
> own Drive.

Tick "Sign in details in this declaration provide full access to all the features".

**Content rating:** questionnaire category *Reference, News or Educational / Utility*; no
violence, sexual content, gambling or user interaction. Expected rating: Everyone / PEGI 3.

**Target audience:** 18 and over (body composition equations are not validated for children).

**Health apps declaration:** Health & Fitness, *Fitness* and *Nutrition*; not a medical
device; no Health Connect permissions are requested.

**Financial features / Government / News:** No.

### Data safety

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Yes, collected** (only when the user turns on Google Drive backup) |
| Is all data encrypted in transit? | Yes |
| Can users request deletion? | Yes (sign out, delete hidden app data in Drive, or uninstall) |
| Shared with third parties? | No |

Data types collected (all: *optional*, purpose *App functionality*, *Account management* for
name and email, processed only to back up to the user's own Drive, not shared):

- Personal info: **Name**, **Email address** (from Google sign in)
- Health and fitness: **Health info** (body measurements, body fat), **Fitness info**
  (workouts, training plan)
- App activity: **Other user generated content** (favourite foods, goals, settings)

Not collected: photos (analysed and stored only on the device), location, contacts,
financial info (Play handles payment), device identifiers, analytics.

**Camera permission:** used only to take scan photos and photograph gym machines.

## 4. Monetisation: Squeeze Pro subscription

**Launch free first.** `PRO_ON_SALE` in `app/build.gradle.kts` is `false`: every feature is
free and nothing about Pro is shown. Once the app is approved and live:

1. Create the subscription below and activate it.
2. Change `PRO_ON_SALE` to `true`, raise `versionCode`, run Release, upload.
3. Update App access (above) and add the FREE AND PRO lines to the full description:
   "The body scan, body fat trend and history are free. Squeeze Pro adds training,
   nutrition, coaching and backup: 7 days free, then monthly or yearly."

Existing users see the Pro screen once after that update.

The app is listed as **Free**. The body scan, body fat trend and history are free forever;
training, workout logging, nutrition, machine scan, coach tips and Drive backup are Pro.

Play Console › Monetise with Play › Products › **Subscriptions** › Create subscription:

1. **Product ID:** `squeeze_pro` (exactly; the app looks for this). Name: Squeeze Pro.
2. Add two **base plans**, both *Auto-renewing*:

   | Base plan ID | Billing period | Price |
   |---|---|---|
   | `monthly` | 1 month | $5.00 (or $4.99) |
   | `yearly` | 1 year | $50.00 (or $49.99) |

   Use "Set prices" to let Play convert to local currencies. Activate both plans.
3. On **each** base plan, **Add offer**: ID `free-trial`, eligibility *New customer
   acquisition: never had this subscription*, phase **Free trial, 7 days**. Activate it.

The app reads prices and the trial from Play, so changing a price never needs a new release.
Users manage or cancel in Google Play; the paywall links there.

Payments need a **payments profile** (Play Console › Setup › Payments profile) and to test
purchases add your account under Setup › License testing (test cards are never charged).

## 5. Testing track

New personal developer accounts must run a **closed test with at least 12 testers for 14
days** before production access. Create the closed testing track, upload the AAB, add testers
by email, and share the opt in link.
