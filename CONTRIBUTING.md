# Contributing to GwaBoard

Thank you for your interest in contributing to GwaBoard!

## Prerequisites

- **Android Studio** Ladybug (2024.2.x) or newer
- **JDK 17** (bundled with Android Studio or installed separately)
- **Android SDK** with API level 36 (compileSdk)
- **Android NDK** r27c (`27.2.12479018`) — required for llama.cpp native build
- **CMake** 3.22.1 — installed via Android SDK Manager

## Repository Structure

```
GwaBoard/
├── shared-models/   Pure Kotlin data classes (SmsMessage, ContactInfo, IpcContract)
├── shared-ipc/      Android library — ContentProvider client + SignatureVerifier
├── shared-crypto/   Android library — Keystore AES-256-GCM
├── keyboard/        APK dev.gwaboard.keyboard (IME service, AI engines, suggestion UI)
├── companion/       APK dev.gwaboard.companion (SMS access, profile builder, ContentProvider)
├── florisboard/     Git submodule (upstream FlorisBoard fork)
├── buildSrc/        Gradle convention plugins
└── gradle/          Version catalog (libs.versions.toml)
```

## Getting Started

1. **Clone the repository** with submodules:
   ```bash
   git clone --recurse-submodules https://github.com/Gwagwa42/GwaBoard.git
   cd GwaBoard
   ```

2. **Open in Android Studio** and let Gradle sync complete.

3. **Build both APKs**:
   ```bash
   ./gradlew :keyboard:assembleDebug :companion:assembleDebug
   ```

4. **Run unit tests**:
   ```bash
   ./gradlew test
   ```

## Building

### Debug builds

```bash
# Build everything
./gradlew assembleDebug

# Build keyboard only
./gradlew :keyboard:assembleDebug

# Build companion only
./gradlew :companion:assembleDebug
```

### Release builds

Release builds require a signing key. Both APKs **must** be signed with the same key for signature-level IPC to work.

```bash
./gradlew :keyboard:assembleRelease :companion:assembleRelease
```

## Testing

```bash
# All unit tests
./gradlew test

# Keyboard tests only
./gradlew :keyboard:test

# Companion tests only
./gradlew :companion:test

# Android instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Architecture Notes

- **Dependency rule**: `:keyboard` never depends on `:companion`. IPC is runtime-only via ContentProvider.
- **Both APKs must be signed with the same key** for signature-level permission verification to work.
- **Keyboard permissions**: `BIND_INPUT_METHOD` only — zero SMS, zero internet, zero contacts.
- **Companion permissions**: `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `READ_CONTACTS`.
- **AI inference**: 100% on-device. Tier 1 is N-gram (Kotlin), Tier 2 is SmolLM2-360M via llama.cpp NDK/JNI.

## Code Style

- Kotlin with standard Android conventions
- Jetpack Compose for UI components
- Koin for dependency injection
- JUnit 5 for unit tests
- Follow existing patterns in the codebase

## Submitting Changes

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes with descriptive commits
4. Ensure all tests pass (`./gradlew test`)
5. Open a pull request against `main`
