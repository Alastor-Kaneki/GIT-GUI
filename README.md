# GIT GUI

A native Android GUI for Git with an AMOLED-only interface, smooth animated rainbow outlines, local repository management, and optional GitHub account integration.

## Current features

- Clone and initialize repositories without Termux
- App-private repository workspace
- Working-tree status, stage, unstage, commit, diff, branches, and commit history
- Fetch, pull, and push over HTTPS
- GitHub OAuth device login or personal-access-token fallback
- GitHub repository browser and one-tap cloning
- Command center for `status`, `add`, `reset`, `restore`, `commit`, `log`, `diff`, `branch`, `checkout`, `switch`, `fetch`, `pull`, `push`, `merge`, `rebase`, `cherry-pick`, `revert`, `stash`, `tag`, `clean`, `remote`, `config`, `rev-parse`, and `show`
- AMOLED black Material 3 UI
- Animated rainbow outlines with speed and direction controls
- Git-inspired custom adaptive icon
- GitHub Actions debug APK builds

## GitHub account setup

1. Create a GitHub OAuth App in GitHub developer settings.
2. Enable Device Flow for the OAuth App.
3. Open GIT GUI settings and paste the OAuth App client ID.
4. Tap Device login and authorize the displayed code.

A client secret is not used or stored by the Android app. The access token is encrypted with Android Keystore before it is saved.

## Build

Use Android Studio with JDK 17 and Android SDK 36, or run:

```bash
./gradlew :app:assembleDebug
```

The wrapper scripts securely bootstrap the official Gradle wrapper JAR on first use and verify its published SHA-256 checksum. The included GitHub Actions workflow also builds an APK and uploads it as the `GIT-GUI-debug` artifact.

## Engine note

The embedded engine uses Eclipse JGit, so it works without Termux or a separately installed Git executable. The command center implements the listed day-to-day Git commands. Low-level plumbing commands and commands that depend on external Git extensions are not yet available.

## License

No license has been selected yet.
