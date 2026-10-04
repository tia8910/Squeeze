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

# On-device models. MediaPipe and ONNX Runtime call into Java from native code, and
# MediaPipe's task options are protobuf messages built by reflection; R8 cannot see either
# use and would strip or rename them, which crashes the scan only in release builds.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class ai.onnxruntime.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# Sign in with Google: the credential type is resolved by class name at runtime.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.playservices.** { *; }

# Annotation-only references from Tink and Guava-style libraries.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# Compile-time annotation-processing types (javax.lang.model, javapoet, AutoValue) that
# some libraries reference but never touch on Android.
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.tools.**
-dontwarn com.squareup.javapoet.**
-dontwarn com.google.auto.value.**
-dontwarn com.google.auto.service.**
-dontwarn autovalue.shaded.**
