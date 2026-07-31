# GIT GUI

An Android GUI for Git with an AMOLED-only interface, animated rainbow outlines, local repository management, and GitHub account integration.

## Current capabilities

- Initialize and clone repositories into app-managed storage
- Browse repository status, branches, stashes, tags, and recent commits
- Stage, unstage, commit, fetch, pull, and push
- Create, check out, and delete branches
- Stash, apply, and drop changes
- Create tags
- Merge, rebase, cherry-pick, hard reset, and clean
- Search a broad catalog of Git porcelain and plumbing commands
- Connect to GitHub using OAuth Device Flow or an access token
- Browse accessible GitHub repositories and clone them
- Encrypt the stored GitHub token using Android Keystore AES-GCM
- Toggle rainbow animation, direction, speed, immersive mode, and haptics
- Use a modified Git-inspired adaptive icon

The command catalog includes Git commands that are not yet represented by dedicated GUI forms. Entries marked `GUI` are connected to native app actions; entries marked `Catalog` are reference entries planned for later handlers.

## GitHub login setup

Token login works without build-time configuration.

For the **Connect with GitHub** device-code button:

1. Create a GitHub OAuth App.
2. Enable **Device Flow** in the OAuth App settings.
3. Add its client ID as `GITHUB_CLIENT_ID` in `~/.gradle/gradle.properties`, or as a repository Actions secret named `GITHUB_CLIENT_ID`.
4. Build the app.

No OAuth client secret is embedded in the Android app.

## Build

The project uses:

- Android Gradle Plugin 8.11.2
- Gradle 8.13
- Kotlin 2.2.20
- Jetpack Compose BOM 2026.06.00
- JGit 6.10.1
- Minimum Android 8.0 / API 26
- Target Android 15 / API 35

Open the repository in Android Studio and build the `app` module, or run the included GitHub Actions workflow.

## Storage

Repositories are kept under the app-specific external files directory in a `repositories` folder. Uninstalling the app can remove app-specific storage, so important work should be pushed to a remote before uninstalling.

## Authentication notes

GitHub passwords and two-factor codes are never collected by the app. Device Flow authorizes through GitHub in the browser. Access tokens are encrypted at rest with a key held by Android Keystore.
