# Consumer ProGuard rules for :shared-ipc
# These rules are applied to consuming modules (keyboard, companion)

# Keep IPC client public API
-keep class dev.gwaboard.shared.ipc.SmsProviderClient { *; }
-keep class dev.gwaboard.shared.ipc.SmsProviderContract { *; }
-keep class dev.gwaboard.shared.ipc.SignatureVerifier { *; }
