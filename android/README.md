# OpenJarvis Android companion

The Android client is a native Kotlin + Jetpack Compose application.

## Current scope

- Connects to an existing OpenJarvis API server over HTTPS.
- Authenticates requests with `Authorization: Bearer <key>`.
- Discovers available models through `GET /v1/models`.
- Streams assistant replies from `POST /v1/chat/completions` using
  OpenAI-compatible Server-Sent Events.
- Sends the full conversation context with each chat request.
- Saves the latest 40 user/assistant messages in app-private local storage.
- Encrypts the server API key with an AES-GCM key held by Android Keystore.
- Provides a clear-history action; conversation history stays on this device.
- Does not request microphone, contacts, calendar, accessibility, or
  notification permissions.

## Build and test

From the repository root:

```bash
cd android
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions runs the same unit-test and debug-APK build tasks. The APK is
produced at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Server requirements

Run OpenJarvis with the server extra and configure an API key before exposing
it beyond loopback. Use a trusted HTTPS endpoint. The app rejects plain HTTP
connections and does not place API keys in URLs.

## Privacy and limitations

Chat history is stored locally in app-private SharedPreferences, is bounded to
40 messages, and is not encrypted separately in this iteration. Clear it from
the app when needed. The API key is encrypted using Android Keystore. Do not
use this preview for highly sensitive conversations until encrypted history
storage and a user-facing connection-management flow are implemented.

This milestone does not yet include wake-word detection, speech recognition,
text-to-speech, document/video capture, app launching, typing into other apps,
Gmail/Calendar OAuth, or on-device model inference. Those remain separate
milestones with dedicated permission, privacy, and device testing.
