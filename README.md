# GIT GUI

A native Android GUI for Git with an AMOLED-only interface, smooth animated rainbow controls, local repository management, direct GitHub account authorization, and an expanded embedded command engine.

## Current features

- Clone and initialize repositories without Termux
- App-private repository workspace
- Working-tree status, stage, unstage, commit, diff, branches, and commit history
- Fetch, pull, and push over HTTPS
- One-tap GitHub Device Flow authorization with no in-app credential entry
- GitHub repository browser and one-tap cloning
- Broad Git command coverage across workspace operations, history, branches, remotes, submodules, objects, refs, search, archives, and maintenance
- AMOLED black Material 3 UI
- Faster animated rainbow outlines with reversible direction
- Animated rainbow text-field outlines and toggles
- Git-inspired custom adaptive icon
- GitHub Actions debug APK builds

See [SUPPORTED_COMMANDS.md](SUPPORTED_COMMANDS.md) for the complete embedded command list and scope. Run `git help` in the app to view the categorized list directly in the command center.

## GitHub authorization builds

The OAuth Client ID is supplied only at build time and is not stored in repository source files.

For GitHub Actions builds, add a repository Actions secret named `GIT_GUI_OAUTH_CLIENT_ID`. Local builds can provide it through either an environment variable or Gradle property:

```bash
GITHUB_CLIENT_ID=your_client_id gradle :app:assembleDebug
```

or:

```bash
gradle :app:assembleDebug -PGITHUB_CLIENT_ID=your_client_id
```

A client secret is not used or stored by the Android app. The access token received after authorization is encrypted with Android Keystore before it is saved.

## Build

Use Android Studio with JDK 17 and Android SDK 36, or run:

```bash
gradle :app:assembleDebug
```

The included GitHub Actions workflow builds an APK and uploads it as the `GIT-GUI-debug` artifact.

## Engine note

The embedded engine uses Eclipse JGit, so it works without Termux or a separately installed Git executable. Commands that depend on native desktop executables, external helpers, Git LFS, or functionality absent from JGit are excluded rather than falsely reported as supported.

## License

No license has been selected yet.
