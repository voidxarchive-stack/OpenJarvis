# OpenJarvis Android companion

The Android client is a native Kotlin + Jetpack Compose application.

## Current scope

- Connects to an existing OpenJarvis API server over HTTPS.
- Authenticates requests with `Authorization: Bearer <key>`.
- Discovers available models through `GET /v1/models`.
- Streams assistant replies from `POST /v1/chat/completions` using
  OpenAI-compatible Server-Sent Events.
- Sends prior user/assistant messages as conversation context.
- Saves the latest 40 messages in app-private local storage.
- Encrypts the server API key with an AES-GCM key held by Android Keystore.
- Offers voice dictation through Android's system speech-recognition activity.
  Recognized text is inserted into the compose box for review before sending.
- Offers Android TextToSpeech playback, including an opt-in automatic
  speak-replies toggle.
- Adds opt-in, foreground-only offline wake-word detection for **"Hey Jarvis"**
  using the open-source openWakeWord Android runtime. The user explicitly
  grants microphone permission and starts/stops listening.

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

Chat history is stored in app-private SharedPreferences, is bounded to 40
messages, and is not uploaded separately. It is not separately encrypted in
this iteration. Clear it from the app when needed. The API key is encrypted
using Android Keystore. Do not use this preview for highly sensitive
conversations until encrypted history storage and a user-facing
connection-management flow are implemented.

Speech dictation opens the platform recognizer UI and requires the user to
start the capture. Recognized text is not sent automatically; the user can
review and edit it before pressing Send. Spoken output uses the device's
installed text-to-speech engine and can be disabled with the auto-speak
toggle. TTS is stopped and released when the screen is disposed.

Wake-word detection uses the open-source openWakeWord Android runtime and its
built-in **"Hey Jarvis"** model. Detection runs on-device without a Picovoice
account, AccessKey, subscription, or cloud audio upload. The user must grant
Android microphone permission and tap **Start listening**. Listening stops
when the user taps Stop or the app leaves the foreground; no background
microphone service or boot-time auto-start is enabled.

The runtime code is Apache-2.0. The bundled pretrained wake-word models are
CC BY-NC-SA 4.0, so this model is intended for non-commercial use. A future
commercial distribution must replace it with a model whose license permits
that use.

Background microphone service, document/video capture, app launching, typing
into other apps, Gmail/Calendar OAuth, and on-device model inference remain
separate milestones with dedicated permission, privacy, lifecycle, and
device testing.
