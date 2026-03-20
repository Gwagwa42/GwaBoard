# GwaBoard Companion — ProGuard / R8 rules

# ── ContentProvider ──
# Keep the ContentProvider (referenced by name in AndroidManifest.xml)
-keep class dev.gwaboard.companion.provider.SmsContentProvider { *; }

# ── kotlinx.serialization ──
# Keep serializable classes and their serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.gwaboard.**$$serializer { *; }
-keepclassmembers class dev.gwaboard.** {
    *** Companion;
}
-keepclasseswithmembers class dev.gwaboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── ContentProvider IPC classes ──
# Keep IPC contract and signature verifier (used in provider registration)
-keep class dev.gwaboard.shared.ipc.SmsProviderContract { *; }
-keep class dev.gwaboard.shared.ipc.SmsProviderClient { *; }
-keep class dev.gwaboard.shared.ipc.SignatureVerifier { *; }

# ── Shared models ──
# Keep data classes used in IPC serialization
-keep class dev.gwaboard.shared.models.** { *; }
