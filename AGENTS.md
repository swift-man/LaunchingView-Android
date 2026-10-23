# AGENTS.md

## Repository Guidelines

- This repository contains the native Android implementation of LaunchingView, the Jetpack Compose launch gate built on `me.gorani:launching-service`.
- Do not introduce Kotlin Multiplatform.
- The `launching-view` module depends on Compose, `activity-compose` (for `BackHandler`), and `lifecycle-runtime-compose`. Keep it free of ViewModel, Fragment, and Firebase dependencies; Firebase access belongs to `launching-service`.
- Keep state transitions in the pure `LaunchingReducer` state machine and cover every branch with JVM unit tests. Composables only render `LaunchingState` and send `LaunchingAction`s.
- Preserve the observable behavior documented in README.md and the iOS `LaunchingView` package; the README maps each iOS branch to its Android counterpart.
- Keep presentation examples in the `sample` module using MVVM, StateFlow, and Jetpack Compose. The sample must compile and run without Firebase credentials.
- Follow SOLID design principles and inject the launching service, default text, and URI opener boundaries for tests.

## Build And Test

Run these commands after code changes:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew :launching-view:publishToMavenLocal
./gradlew :launching-view:dokkaGeneratePublicationHtml
```

Tests must not make Firebase network calls. Add regression coverage for every behavior change.

Generated Dokka HTML belongs in `launching-view/build/dokka/html` and must not be committed. The `Deploy Dokka` workflow publishes that directory to the `main` branch of `swift-man/docs` under `LaunchingView-Android/`. Keep `keep_files: true` so deployments do not remove other packages. The workflow requires a `DOCS_DEPLOY_KEY` repository secret whose public key has write access to `swift-man/docs`.

## Pull Requests

- Create Ready pull requests, never draft pull requests.
- Prefix PR and commit titles with an appropriate type such as `feat.`, `fix.`, `docs.`, or `chore.`.
- Do not use `[Codex]` or `[codex]` in PR titles.
- Address review threads with evidence and identify false positives explicitly.
- Delete merged feature branches.
