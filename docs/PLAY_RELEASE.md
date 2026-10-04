# Releasing Squeeze.fit on Google Play

Everything the Play Console asks for, in the order it asks. The app side is done: target SDK
36, signed AAB from the Release workflow, R8 rules checked on every CI run, privacy policy
live on the website.

## 1. One time setup

### Upload key and GitHub secrets

1. Create an upload key on your computer (keep the file and passwords safe, never commit them):

   ```
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10950
   ```

2. In GitHub, Settings › Secrets and variables › Actions, add:

   | Secret | Value |
   |---|---|
   | `KEYSTORE_BASE64` | `base64 -w0 upload.jks` output |
   | `KEYSTORE_PASSWORD` | the keystore password |
   | `KEY_ALIAS` | `upload` |
   | `KEY_PASSWORD` | the key password |
   | `PLAY_PUBLIC_KEY` | Play Console › Monetise › Monetisation setup › Licensing (base64 RSA key) |

### Build the release

Push a tag (`git tag v1.0.0 && git push origin v1.0.0`) or run the **Release** workflow by
hand. It produces `squeeze-1.0.0.aab` (upload this) plus a signed APK and the R8 mapping file
in the `squeeze-release` artifact.

### Play App Signing and Google sign in

Upload the first AAB with Play App Signing on (the default). Then:

1. Play Console › Test and release › App integrity › copy the **App signing key** SHA-1
   (and the upload key SHA-1).
2. Google Cloud, project **Squeeze** (71286678065) › Clients › Create client › Android:
   package `com.squeeze.app`, the App signing SHA-1. Add a second one with the upload key
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

**App access:** All functionality is available without special access. (Google sign in is
optional.)

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

## 4. Monetisation

Create these in Play Console › Monetise › Products › In app products before testing purchases:

| Product ID | Type |
|---|---|
| `squeeze_pro_lifetime` | One time, non consumable |
| `squeeze_training_block` | One time, consumable |

## 5. Testing track

New personal developer accounts must run a **closed test with at least 12 testers for 14
days** before production access. Create the closed testing track, upload the AAB, add testers
by email, and share the opt in link.
