# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Fixed

- Re-creating a foreground Activity (rotation, in-app theme change) restores the last policy and the dialogs that were open, no longer reshows a dismissed dialog, and no longer fetches again unless a fetch was running or had failed. An Activity re-created on a foreground return or after process death still fetches.
- The Release workflow fails at its first step with a clear error when any Maven Central or signing secret is missing, and can be re-run manually with `workflow_dispatch`.

### Added

- Added Robolectric tests for the Compose host: lifecycle driven fetches and saved-state restoration.

- Added the first native Android implementation of LaunchingView: `LaunchingView`, `AsyncLaunchingView`, the blocking launch screen, and the optional-update, notice, and fetch-error dialogs.
- Added `LaunchingAlertDefaultText` for fallback alert text.
- Added a Compose sample that switches between launch policies with a fake `LaunchingServiceClient`.
- Added Android CI, Dokka deployment, and Maven Central publication configuration.
