# Jarvis Android companion

A native Kotlin + Jetpack Compose Android companion built around voice, personal context, visual help and user-controlled phone actions.

## App areas

- **Home**: Jarvis core, wake-word controls, activity metrics and shortcuts.
- **Chat**: streaming OpenAI-compatible chat, local message history, speech dictation, spoken replies and local app-launch commands.
- **Live**: a dedicated Jarvis Live screen with Android MediaProjection screen sharing, a local preview and a one-shot “Ask Jarvis about current screen” action.
- **Memory**: user-controlled local notes. Add or remove memories here, or start a chat with `Remember that …`; notes are supplied as future chat context.
- **Hub**: installed-app launcher, Calendar, alarms, Maps and browser shortcuts, plus integration status.
- **Settings**: persistent HTTPS endpoint, encrypted API key, model, reply language and companion personality.

The default personality is friendly, lightly humorous and intellectually honest. Jarvis is instructed to teach step by step, explain uncertainty and respectfully correct mistakes instead of automatically agreeing.

## Voice and app launching

- Tap **Activate voice** or start the opt-in offline **Hey Jarvis** listener, then speak.
- Explicit commands such as “open GPay”, “launch WhatsApp”, “GPay kholo” and “Google Pay open karo” are resolved locally against installed launcher apps.
- The app launcher works for installed apps that expose a launcher activity and are visible to Android's package-visibility query.
- App launching only opens the selected application. It does not tap buttons, send messages, initiate transfers or perform transactions inside that app.
- Android's speech recognizer is used for capture; the transcript is reviewed before ordinary chat messages are sent.
- Android TextToSpeech reads responses aloud; automatic speaking can be disabled.

Wake-word detection requires microphone permission and an explicit Start action. It remains foreground-only in this preview. The openWakeWord runtime is Apache-2.0; the bundled pretrained wake-word model is CC BY-NC-SA 4.0 and is therefore non-commercial. Replace it with a suitably licensed model before commercial distribution.

## Live screen sharing

- Tap **Share screen** and approve Android's system capture prompt each time.
- Android may let the user share a single app window or the full display.
- A foreground media-projection service keeps the user-approved session active and shows an ongoing notification.
- Jarvis samples a downscaled frame locally for the Live preview. It pauses capture while the Jarvis activity is visible to avoid showing a recursive preview.
- A frame is sent to the configured AI endpoint only when the user taps **Ask Jarvis about current screen**.
- The selected endpoint/model must support OpenAI-compatible `image_url` message content. Text-only models or servers may reject vision requests; the app reports the server error.
- This is a visual screen-context preview, not yet Gemini-style continuous two-way live voice/video streaming. Screen content is not continuously uploaded.

## Persistence and privacy

- Server URL, selected model, personality, language and speak-replies preference are saved automatically as they change.
- The API key remains encrypted with AES-GCM using a non-exportable Android Keystore key.
- Chat history and personal memory remain in app-private SharedPreferences and are not separately encrypted in this preview. Do not store passwords, recovery codes or highly sensitive secrets.
- The live frame is held in memory only and is not added to persistent chat history.
- Screen capture requires explicit Android consent for every session. Stop sharing in Jarvis or use Android's system screen-sharing stop control to end it.

## Build and test

From the repository root:

```bash
cd android
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions runs the same unit-test and debug-APK build tasks. The APK is produced at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Server connection

The app connects to an OpenAI-compatible OpenJarvis endpoint:
- `GET /v1/models` discovers available model IDs.
- `POST /v1/chat/completions` streams assistant replies using Server-Sent Events.
- Requests use `Authorization: Bearer <key>`.
- HTTPS is required; credentials are never placed in URLs.

## Still planned

Gmail and Google Calendar OAuth, calendar event reading and proactive meeting alerts, PDF/image import from the Android share sheet, accessibility-based typing with user approval, built-in web research tools, background wake-word listening and local LLM inference are not implemented in this preview. They should be added as separate permission-reviewed milestones.
