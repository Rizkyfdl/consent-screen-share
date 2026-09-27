# Consent Screen Share

This repository contains two native Kotlin Android applications:

- `target-app`: explicitly approved MediaProjection screen sender.
- `observer-app`: WebSocket JPEG frame viewer.
- `screen-relay`: small Node.js WebSocket relay.

The target always runs a visible foreground service notification. Accessibility
is intentionally not requested because it is not needed to capture the screen.

## Build

Open this directory in Android Studio with JDK 17 and Android SDK 35 installed,
then run:

```bash
./gradlew :target-app:assembleDebug
./gradlew :observer-app:assembleDebug
```

The APKs will be under:

```text
target-app/build/outputs/apk/debug/target-app-debug.apk
observer-app/build/outputs/apk/debug/observer-app-debug.apk
```

## Release build without committing signing secrets

The Gradle files read signing values only from environment variables. The
keystore should live outside this project and real passwords must never be
placed in `build.gradle.kts`, `release-config.env.example`, or Git.

Create a keystore outside the project, for example:

```bash
mkdir -p "$HOME/.android"
keytool -genkeypair \
  -v \
  -keystore "$HOME/.android/consent-screen-share-upload.jks" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias consent-upload
```

`keytool` asks for the passwords interactively. For a local release build,
export the values in the current shell without writing them to the project:

```bash
export ANDROID_KEYSTORE_FILE="$HOME/.android/consent-screen-share-upload.jks"
export ANDROID_KEYSTORE_PASSWORD='use-your-secret-store'
export ANDROID_KEY_ALIAS='consent-upload'
export ANDROID_KEY_PASSWORD='use-your-secret-store'

./gradlew :target-app:assembleRelease
./gradlew :observer-app:assembleRelease
```

In CI, define the same four variables using the CI secret manager. If they
are not all present, Gradle leaves the release APK unsigned instead of
guessing or embedding credentials.

### GitHub Actions release

The included `.github/workflows/release.yml` builds both signed APKs and
publishes them when a `v*` tag is pushed. It also publishes a SHA-256 file for
each APK so downloads can be verified. Add these GitHub repository secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 contents of the JKS file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: upload key alias.
- `ANDROID_KEY_PASSWORD`: upload key password.

To create the base64 value without putting the JKS in the repository:

```bash
base64 -w 0 "$HOME/.android/consent-screen-share-upload.jks" > upload-key.base64
```

Copy the contents of that temporary file into the
`ANDROID_KEYSTORE_BASE64` repository secret, then remove the temporary file.
Trigger the workflow manually or push a tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Verify a downloaded APK on Linux or macOS:

```bash
sha256sum -c target-app-release.apk.sha256
sha256sum -c observer-app-release.apk.sha256
```

## Relay

```bash
cd screen-relay
npm install
RELAY_TOKEN="replace-with-a-long-random-token" node server.js
```

For production, provide a certificate and key to enable WSS:

```bash
RELAY_TOKEN="replace-with-a-long-random-token" \
TLS_KEY_FILE="/etc/letsencrypt/live/example.com/privkey.pem" \
TLS_CERT_FILE="/etc/letsencrypt/live/example.com/fullchain.pem" \
PORT=8443 \
node server.js
```

Set both Android apps to:

```text
wss://example.com:8443
```

The certificate must be trusted by Android. `ws://` is only for a trusted
local network during development.

## Consent and lifecycle

The target app intentionally remains visible, requires the system
MediaProjection approval, and displays an ongoing foreground notification.
If the user explicitly enables the resume option, a reboot only creates a
visible notification that opens the app. The user must press Start and approve
a new MediaProjection dialog; capture never starts automatically. It does not
hide its launcher entry or start covertly after a reboot.

Connection settings are stored in the app's private local preferences so the
server URL, room, and token are restored when the app opens. The token field
is masked in the UI; use a per-session token for production deployments.