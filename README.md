# Codex Remote for Android

[![Android CI](https://github.com/YM040923/codex-remote-android/actions/workflows/android.yml/badge.svg)](https://github.com/YM040923/codex-remote-android/actions/workflows/android.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

An Android client for Codex hosts reached over SSH. A saved connection represents
one host; after connecting, the app imports every resumable remote Codex
conversation and groups projects from each thread's working directory. The app
does not run a local agent or create a second app-server: it connects through
`codex app-server proxy` to the host's shared app-server daemon.

Implementation notes and the audited Codex source boundary are documented in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
The current Desktop parity matrix and known gaps are tracked in
[`docs/FEATURE_PARITY.md`](docs/FEATURE_PARITY.md).

## Remote host requirements

- OpenSSH access with a password or PEM/OpenSSH private key.
- `codex` installed and available from the remote login shell (`codex --version`).
- The Codex app-server daemon running for the same remote user. Android reports a
  clear setup error if the shared daemon is unavailable; it never starts a second
  app-server that would conflict with conversations already open elsewhere.
- A Codex account on the remote host. Existing API-key/ChatGPT auth is reused;
  when required, the app can start Codex's remote ChatGPT device-code login.
- A trusted, least-privilege remote user.

For live control of a conversation already open in Codex Desktop, both clients
must connect to the same daemon under the same remote user and `CODEX_HOME`.

No project path is configured on Android. Existing projects and conversations
come from the remote Codex history returned by `thread/list`.
Opening a conversation resumes its app-server subscription while loading only
the latest five full turns; older turns are fetched as the chat is scrolled to
the top. When Desktop and Android connect to the same shared daemon, Android
rejoins the existing live turn, can stop it, and can then send a new message in
that same conversation. If `thread/resume` reports that a different
app-server process owns the writer lease, Android instead shows a read-only
history/status snapshot refreshed every five seconds; it does not offer stop or
send controls that the connected server cannot safely route to that process.
Connect both clients to the same daemon to control the original task. Android
never kills a CLI process or removes lock files. Forking from a read-only
snapshot remains available when a separate conversation is preferred.

The model and reasoning pickers are also remote data. They are loaded from
`model/list`, so the choices follow the Codex version and account configured on
that SSH host rather than a hard-coded Android catalog.

The composer uses the same remote app-server surfaces for Plan mode, service
tiers, permission profiles, image input, running-turn steering, Goals, context
compaction, forks, code review, MCP status, remote skills and installed plugins.
Task pins are stored on the remote Codex thread rather than only on Android.

## Install

Download an APK from the latest
[GitHub release](https://github.com/YM040923/codex-remote-android/releases/latest).
Only the artifact without `-unsigned` is directly installable as a production
build; an `-unsigned` artifact requires signing before installation.
Android may ask you to allow installation from your browser or file manager.

The project is an unofficial community client. It is not affiliated with or
endorsed by OpenAI. Codex and OpenAI are trademarks of their respective owner.

## Build

Open this directory in Android Studio, or run `./gradlew assembleDebug` with
JDK 17 and Android SDK 35 installed. The app version is shown in Android as
configured in the app build; each build also copies a clearly named APK into
`app/build/outputs/named-apk/`, such as
`Codex-Remote-v<version>-debug.apk`. The original Gradle APK output is retained.

Run the device-side regression suite on a connected emulator or Android device:

```text
./gradlew connectedDebugAndroidTest
```

The first SSH handshake asks you to verify its SHA-256 host-key fingerprint
before any password or private key is sent. Subsequent key changes are blocked
until the saved fingerprint is explicitly cleared by editing the connection.

To produce a signed release build, provide these environment variables before
running `./gradlew assembleRelease`:

```text
CODEX_REMOTE_KEYSTORE_PATH
CODEX_REMOTE_KEYSTORE_PASSWORD
CODEX_REMOTE_KEY_ALIAS
CODEX_REMOTE_KEY_PASSWORD
```

Signing material must remain outside the repository. APKs, keystores, local SDK
configuration, build caches, and QA captures are excluded by `.gitignore`.
Without release signing configured, the named release artifact is explicitly
marked `-release-unsigned.apk` so it cannot be mistaken for a signed installable
release.

## License

Licensed under the [Apache License 2.0](LICENSE).
