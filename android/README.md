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
- Adds opt-in, foreground-only wake-word detection using Picovoice Porcupine.
  The user grants microphone permission and explicitly starts/stops listening.

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

Speech dictation opens the platform recognizer UI. Recognized text is not
sent automatically; the user can review and edit it before pressing Send.
Spoken output uses the device's installed text-to-speech engine and can be
disabled with the auto-speak toggle.

Wake-word detection uses Picovoice Porcupine's on-device engine. The user
must enter a Picovoice AccessKey, grant Android microphone permission, and
tap **Start listening**. The current build uses the built-in **"Jarvis"**
keyword (not the custom phrase "Hey Jarvis"). Listening stops when the user
taps Stop or the app leaves the foreground; no background microphone service
or boot-time auto-start is enabled. A custom "Hey Jarvis" trigger requires a
custom Android `.ppn` keyword model from the
[Picovoice Console](https://console.picovoice.ai/). Keep the AccessKey private;
the app stores it encrypted with Android Keystore.

Document/video capture, app launching, typing into other apps, Gmail/Calendar
OAuth, and on-device language-model inference remain separate milestones.