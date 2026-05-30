# Copilot instructions for FT8AF

## Repository identity

This repository is `jonstacks/FT8AF`, a fork in the FT8CN project lineage. The Android Gradle project lives in `ft8cn/`; run Gradle commands from that directory only.

Treat this fork as its own product. It may intentionally diverge from upstream FT8CN repositories, so do not assume upstream behavior, naming, UI, packaging, or release practices are automatically authoritative here.

## Cloud-agent build environment

Use the CI-like Linux environment configured by `.github/workflows/copilot-setup-steps.yml`, not the workstation-specific guidance in `CLAUDE.md`, as the cloud-agent source of truth.

Expected toolchain:

- Ubuntu GitHub-hosted runner
- Temurin JDK 17
- Gradle wrapper 8.4 from `ft8cn/gradlew`
- Android Gradle Plugin 8.2.2
- Kotlin Android plugin 1.9.22
- Android SDK Platform 35
- Android Build-Tools 35.0.0
- app module: `ft8cn/app`
- `applicationId`: `radio.ks3ckc.ft8af`
- Android namespace: `com.bg7yoz.ft8cn`

Cloud agents should prefer deterministic build/test flows and should not assume a physical Android device, `adb` target, or emulator is available unless the task explicitly requires device testing.

## Standard validation commands

Before and after relevant code changes, prefer the same commands used by CI:

```bash
cd ft8cn && ./gradlew testDebugUnitTest --stacktrace
cd ft8cn && ./gradlew assembleRelease
```

Unsigned release APKs are acceptable when signing secrets are unavailable. Only use `bundleRelease` when release signing inputs are present and the task requires it.

## Issue workflow

For issues opened directly in this fork:

1. Reproduce or inspect the behavior against the current `jonstacks/FT8AF` code.
2. Make the smallest change appropriate for this fork.
3. Validate with the standard Gradle commands when applicable.
4. Reference the fork issue in commits and PR descriptions when relevant.

For issues, bugs, or features originating upstream:

1. Identify the upstream repository, issue/PR number, and URL.
2. Summarize the upstream report in FT8AF fork context.
3. Check whether the issue is already fixed, still reproducible, not applicable, or a good candidate to fix here.
4. Compare upstream assumptions against this fork's current code and product direction before changing anything.
5. Implement fixes only in this fork unless explicitly instructed otherwise.
6. Link the upstream reference in the PR description and any useful commit messages.

Do not blindly port upstream patches. Prefer adapting the underlying fix to FT8AF's current code, UI, package identity, release process, and tests.

## Fork Maintenance Agent profile

When asked to perform upstream issue triage or fork maintenance, operate as a "Fork Maintenance Agent":

- Keep `jonstacks/FT8AF` as the working repository and source of truth.
- Fetch or inspect upstream context only to understand the report, patch, or behavior.
- Classify upstream issues as: already fixed in this fork, reproducible in this fork, not applicable to this fork, or candidate for a fork-specific fix.
- Explain any intentional fork divergence before changing code that would make FT8AF more like upstream.
- Validate changes against this fork's current CI/build commands, not upstream-only workflows.
