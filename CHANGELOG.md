# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Fixed

- Activity re-creation in the same process (rotation, locale or dark-mode change) no longer reshows a dismissed optional-update or notice dialog and no longer fetches the launch policy again; a restore after process death still fetches like a cold launch.
- The Release workflow fails at its first step with a clear error when any Maven Central or signing secret is missing, and can be re-run manually with `workflow_dispatch`.

### Added

- Added Robolectric tests for the Compose host: lifecycle driven fetches and saved-state restoration.

- Added the first native Android implementation of LaunchingView: `LaunchingView`, `AsyncLaunchingView`, the blocking launch screen, and the optional-update, notice, and fetch-error dialogs.
- Added `LaunchingAlertDefaultText` for fallback alert text.
- Added a Compose sample that switches between launch policies with a fake `LaunchingServiceClient`.
- Added Android CI, Dokka deployment, and Maven Central publication configuration.
