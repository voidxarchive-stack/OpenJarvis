# OpenJarvis Android companion (MVP)

This is the first native Android client for OpenJarvis. It is deliberately a
small, testable foundation rather than a claim that the full Jarvis 2.0
experience is complete.

## Current scope

- Native Kotlin + Jetpack Compose application.
- Connects to an existing OpenJarvis API server over HTTPS.
- Authenticates API requests with `Authorization: Bearer <key>`.
- Discovers an available model through `GET /v1/models`.
- Sends a non-streaming chat request to `POST /v1/chat/completions`.
- Encrypts the API key with an AES-GCM key held by Android Keystore.
- Does not request microphone, contacts, calendar, accessibility, or
  notification permissions.

## Build

From the repository root:

```bash
cd android
gradle :app:testDebugUnitTest :app:assembleDebug
```

The CI workflow runs the same build and test tasks on GitHub-hosted runners.
The debug APK is produced at
`android/app/build/outputs/apk/debug/app-debug.apk`.

## Server requirements

Run OpenJarvis with the server extra and configure an API key before exposing
it beyond loopback. The server's `/health` endpoint is intentionally not used
as proof of authentication; the app authenticates by requesting
`/v1/models`.

Use a trusted HTTPS endpoint. The app rejects plain HTTP connections. Do not
put API keys in URLs, screenshots, source files, or issue reports.

## Deliberate limitations in this first slice

- No wake word or background microphone service.
- No speech recognition or text-to-speech.
- No document/video capture or screen reading.
- No app launching or typing into other apps.
- No Gmail or Calendar OAuth integration.
- No local Android inference runtime yet; inference is performed by the
  configured OpenJarvis server.

These are separate milestones because they need their own permission,
privacy, lifecycle, and device tests. The next slices should add streaming
chat and conversation history, then voice interaction, followed by explicitly
approved Android actions and individually connected services.
