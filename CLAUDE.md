# GwaBoard

Privacy-first AI keyboard for Android (Pixel 8A / Tensor G3 / GrapheneOS). Dual-app architecture: a keyboard IME + a companion SMS app communicating via signature-protected ContentProvider.

**Status**: Planning phase — no application source code yet. 20 GitHub issues across 9 milestones.

## Architecture (planned)

```
gwaboard/
├── shared-models/          ← Pure Kotlin data classes (SmsMessage, ContactInfo, IpcContract)
├── shared-ipc/             ← Android lib, ContentProvider client + SignatureVerifier
├── shared-crypto/          ← Android lib, Keystore AES-256-GCM
├── keyboard/               ← APK dev.gwaboard.keyboard (IME service, AI engines, suggestion UI)
├── companion/              ← APK dev.gwaboard.companion (SMS access, profile builder, ContentProvider)
└── florisboard/            ← Git submodule (upstream FlorisBoard fork)
```

**Dependency rule**: `:keyboard` never depends on `:companion`. IPC is runtime-only via ContentProvider.

### AI engine — 2-tier hybrid

| Tier | Engine | Trigger | Latency | RAM |
|------|--------|---------|---------|-----|
| 1 | N-gram (Kotlin) | Every keystroke | <10ms | ~50MB |
| 2 | SmolLM2-360M-Instruct Q4_K_M via llama.cpp NDK/JNI | Pause >300ms or incoming SMS | ~200-500ms | ~400MB |

`HybridSuggestionEngine` orchestrates both tiers. Tier 2 loads lazily, unloads after 30s inactivity.

### Security model

- Keyboard APK: `BIND_INPUT_METHOD` only — zero SMS, zero INTERNET, zero contacts
- Companion APK: `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS`, `READ_CONTACTS`
- IPC: signature-level permission + AES-256-GCM encryption
- All AI inference is 100% on-device

## Build

- **Build system**: Gradle with Kotlin DSL, version catalog (`gradle/libs.versions.toml`), convention plugins in `buildSrc/`
- **Language**: Kotlin (+ C++ for llama.cpp NDK)
- **UI**: Jetpack Compose (suggestion bar)
- **Target**: Android minSdk 24
- **Distribution**: F-Droid + GitHub Releases

## AI Pipeline

This repo uses the `ai-pipeline` submodule (`.ai-workflow/`) for automated Issue-to-PR workflows.

### Key commands

| Command | Usage |
|---------|-------|
| `/issue-to-pr <N>` | Full pipeline: parse issue → implement → test → PR |
| `/milestone-to-prs <N>` | Batch process all issues in a milestone |
| `/milestone-status <N>` | Dashboard for milestone progress |
| `/worktree-status` | List active pipeline worktrees |

### Configuration

`.ai-workflow-config/project.json` needs to be populated before the pipeline can run effectively. Currently unconfigured — key fields to fill:

- `modules` — map the 5 Gradle modules + florisboard submodule
- `build.tool` → `"gradle"`, `build.command` → `"./gradlew"`
- `stack.languages` → `["kotlin"]`, `stack.frameworks` → `["compose", "florisboard"]`
- `stack.architecture` → `"clean-architecture"`
- Test tasks per module

## Key references

- `.ai-workflow/CLAUDE.md` — full pipeline documentation (3-layer architecture, phases, agents, scripts)
- `.superpowers/brainstorm/75178-1773999942/` — design documents (modules, AI engine, security, UX)
- GitHub Issues & Milestones — implementation roadmap (9 phases from scaffolding to distribution)
