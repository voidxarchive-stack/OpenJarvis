# Jarvis Android companion

A native Kotlin + Jetpack Compose Android companion designed around voice, personal context and safe, user-controlled actions.

## Current app experience

- **Home**: Jarvis core orb, wake-word status, connection status and shortcuts to the companion's main areas.
- **Chat**: OpenAI-compatible streaming chat, local conversation history, speech dictation, spoken replies and suggested learning prompts.
- **Memory**: user-controlled local notes. Add or remove memories from the Memory screen, or start a chat message with `Remember that …`. Saved memories are added to the private system context sent with future chats.
- **Hub**: explicit one-tap shortcuts for Calendar, alarms, Maps and web search. Sharing plain text from another app opens a pre-filled Jarvis conversation.
- **Settings**: HTTPS server endpoint, encrypted API key, model selection, response language and editable companion personality.

The default personality is friendly, lightly humorous and intellectually honest. It is prompted to explain uncertainty, teach step by step and respectfully correct mistakes rather than automatically agree.

## Voice

- Android's system speech-recognition activity is used for manually started dictation.
- Android TextToSpeech reads responses aloud; automatic speaking can be disabled.
- Opt-in offline **Hey Jarvis** detection uses the openWakeWord Android runtime and a built-in model.
- Wake-word listening requires microphone permission and an explicit Start action. This preview is foreground-only and stops when the app leaves the foreground. No background mic service or boot-time auto-start is enabled.

The openWakeWord runtime is Apache-2.0. The bundled pretrained wake-word model is CC BY-NC-SA 4.0 and is therefore non-commercial. Replace it with a suitably licensed model before commercial distribution.

## Build and test

From the repository root:

```bash
cd android
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions runs the same unit-test and debug-APK tasks. The APK is produced at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Server connection

The app connects to an OpenAI-compatible OpenJarvis endpoint:
- `GET /v1/models` discovers available model IDs.
- `POST /v1/chat/completions` streams responses using Server-Sent Events.
- Requests use `Authorization: Bearer <key>`.
- HTTPS is required; credentials are never placed in URLs.
- The API key is encrypted using an AES-GCM key in Android Keystore. The selected server URL and model ID are stored in app-private preferences.

## Privacy and current limitations

- Chat history is stored in app-private SharedPreferences and bounded to the latest 40 messages. It is not separately encrypted in this preview.
- Personal memory is stored in app-private SharedPreferences. It is user-managed, but not separately encrypted. Do not save passwords, recovery codes or highly sensitive secrets.
- Shared-content support currently accepts plain text only. PDF parsing, image understanding, video/screen context and live camera understanding are not yet implemented.
- Hub shortcuts launch Android apps or browser destinations only after the user taps them. They do not read or modify those apps' data.
- Gmail and Google Calendar OAuth, calendar event reading, proactive meeting alerts, notification access, accessibility-based app control/typing, web-search tools inside chat, background wake-word listening and local LLM inference are future milestones. The Hub labels these as not connected/not implemented rather than pretending they work.

## Safety model

No message is sent automatically from speech recognition: the user reviews the transcript and taps Send. Sensitive account access and cross-app actions must use explicit Android permissions or official OAuth scopes, with a confirmation step before sending or changing data.
