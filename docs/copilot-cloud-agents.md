# Copilot Cloud Agents

This repository is configured for GitHub Copilot Cloud Agents with `.github/workflows/copilot-setup-steps.yml`.

## Environment

The setup workflow mirrors the existing Android CI assumptions:

- `ubuntu-latest`
- Temurin JDK 17
- Android SDK command-line tools
- Android SDK Platform 35
- Android Build-Tools 35.0.0
- Gradle wrapper from `ft8cn/gradlew`
- Gradle caches for `~/.gradle/caches` and `~/.gradle/wrapper`

The workflow accepts Android SDK licenses non-interactively, sets `JAVA_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, and adds Android tools to `PATH` for the agent environment.

## Commands agents should run

Run Gradle from `ft8cn/`:

```bash
cd ft8cn && ./gradlew testDebugUnitTest --stacktrace
cd ft8cn && ./gradlew assembleRelease
```

`assembleRelease` may produce an unsigned APK when release signing secrets are not available. That is expected for cloud-agent and pull request work.

## Cloud limitations

`CLAUDE.md` contains local Windows/WSL and attached-phone deployment guidance for a human workstation. Cloud agents should not use it as the environment definition.

In cloud-agent sessions, do not assume:

- a connected Android phone,
- a stable `adb` target,
- an emulator,
- release signing secrets.

Prefer unit tests and CI-like Gradle builds unless a task explicitly requires device investigation.

## Fork-aware issue triage

`jonstacks/FT8AF` is a fork in the FT8CN lineage and may intentionally diverge from upstream.

When an issue originates upstream, agents should:

1. Record the upstream issue or PR URL.
2. Summarize the upstream behavior in FT8AF context.
3. Verify whether the behavior exists in this fork.
4. Classify it as already fixed, reproducible, not applicable, or a candidate for a fork-specific fix.
5. Change only this fork unless explicitly instructed otherwise.
6. Link upstream references in PR descriptions and commit messages when relevant.

Upstream is context, not an automatic source of truth for this fork.
