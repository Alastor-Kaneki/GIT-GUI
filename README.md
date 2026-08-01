# GIT GUI

A native Android GUI for Git with an AMOLED-only interface, smooth animated rainbow controls, local repository management, and direct GitHub account authorization.

## Current features

- Clone and initialize repositories without Termux
- App-private repository workspace
- Working-tree status, stage, unstage, commit, diff, branches, and commit history
- Fetch, pull, and push over HTTPS
- One-tap GitHub Device Flow authorization with no in-app credential entry
- GitHub repository browser and one-tap cloning
- Command center for `status`, `add`, `reset`, `restore`, `commit`, `log`, `diff`, `branch`, `checkout`, `switch`, `fetch`, `pull`, `push`, `merge`, `rebase`, `cherry-pick`, `revert`, `stash`, `tag`, `clean`, `remote`, `config`, `rev-parse`, and `show`
- AMOLED black Material 3 UI
- Faster animated rainbow outlines with reversible direction
- Animated rainbow text-field outlines and toggles
- Git-inspired custom adaptive icon
- GitHub Actions debug APK builds

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

The embedded engine uses Eclipse JGit, so it works without Termux or a separately installed Git executable. The command center implements the listed day-to-day Git commands. Low-level plumbing commands and commands that depend on external Git extensions are not yet available.

## License

No license has been selected yet.
