# Room and SQLCipher
-keep class net.zetetic.database.** { *; }
-keep class androidx.sqlite.db.** { *; }

# Play Billing response classes are reflected over by the library
-keep class com.android.billingclient.api.** { *; }

# Purchase verification: keeping this class name stable is deliberate. Local verification
# is defeated by patching the APK regardless of obfuscation, so hiding it buys nothing;
# see PurchaseVerifier for why that exposure is accepted.
-keep class com.squeeze.app.billing.PurchaseVerifier { *; }

# Core domain models are serialised to JSON for programme export/import
-keep class com.squeeze.core.model.** { *; }
-keep class com.squeeze.core.program.** { *; }
