# Jarvis Android companion

A native Kotlin + Jetpack Compose Android companion built around voice, personal context, visual help and user-controlled phone actions.

## App areas

- **Home**: Jarvis core, wake-word controls, activity metrics and shortcuts. The existing Home layout is intentionally kept intact.
- **Chat**: streaming OpenAI-compatible chat with separate conversations, auto-titles, local history, pin/rename/delete actions, speech dictation and spoken replies.
- **Live**: an Android MediaProjection screen-share workspace, local preview, plus menu for screen/app sharing and selected files, and an explicit one-shot Ask action.
- **Memory**: user-controlled notes, separate from conversation history. Add or remove memories here, or start a chat with `Remember that …`; saved notes are supplied as future chat context.
- **Hub**: installed launcher-app list, per-app Jarvis policy (Allow all / Read only / Deny), Calendar, alarms, Maps and browser shortcuts.
- **Settings**: existing HTTPS endpoint, encrypted API key, model, reply language and companion personality settings, plus an About section.

The default reply language for a fresh install is English. Existing saved language preferences are retained. The default personality is friendly, lightly humorous and intellectually honest. Jarvis is instructed to teach step by step, explain uncertainty and respectfully correct mistakes instead of automatically agreeing.

## Chat history vs Memory

- Each conversation is saved independently on-device. Chat offers **New**, **History**, and a three-dot menu for **Pin/Unpin**, **Rename** and **Delete**.
- On the first upgraded run, the previous single-thread chat is migrated into one saved conversation. The legacy chat store is not cleared.
- Deleting a conversation only deletes that one chat. It does not delete Memory notes.
- **Add Memory** is for short details the user explicitly wants Jarvis to reuse (for example, preferred answer language, study goals or explanation style). It is not a chat archive, does not read Gmail or other apps, and does not automatically save every message.
- Conversation text and Memory notes are in separate app-private stores. In this preview, neither store is separately encrypted; do not save passwords, recovery codes or highly sensitive secrets.
- Visual attachment bytes and screen frames are intentionally not persisted in saved conversations.

## Voice and app launching

- Tap **Activate voice** to enable the opt-in offline **Hey Jarvis** listener, then speak. The listener remains foreground-only in this preview and stops when the app leaves the foreground.
- When the wake word is detected, Jarvis opens the Live workspace. The following spoken phrase is placed into the Live question field; explicitly naming a launch command such as “open Instagram” still opens the matching installed app.
- Explicit commands such as “open Instagram”, “open GPay”, “launch WhatsApp”, “Instagram kholna” and “GPay kholo” are resolved locally against installed launcher apps.
- App discovery is limited to launcher apps visible to Android's package-visibility rules, not every hidden/system package without a launcher activity.
- Hub exposes a per-app Jarvis policy: **Allow all**, **Read only** or **Deny**. Gmail, Google Pay, PhonePe, Paytm and detected banking/UPI/wallet apps default to **Deny**; other visible launchable apps default to **Read only**.
- These three settings are Jarvis-side controls, not Android OS permission grants. Current supported phone action is launching a selected app. Allow all does not currently mean Jarvis can tap buttons, type messages or access app data; those actions require separate features and Android approval. Deny blocks Jarvis' own app-launch command for that package.
- Opening an app does not perform payments, transfers, messages or transactions inside it.
- Android's speech recognizer is used for speech capture; the transcript is reviewed before ordinary chat messages are sent. Android TextToSpeech reads responses aloud; automatic speaking can be disabled.

Wake-word detection requires microphone permission and an explicit Start action. It remains foreground-only in this preview. The openWakeWord runtime is Apache-2.0; the bundled pretrained wake-word model is CC BY-NC-SA 4.0 and is therefore non-commercial. Replace it with a suitably licensed model before commercial distribution.

## Live screen and file sharing

- Tap **Share app or screen** or use the **+** menu and approve Android's system capture prompt each time.
- Android's capture dialog may offer a single-app window or the full display. The user chooses; Jarvis cannot bypass or silently choose the app for you. Exact picker choices depend on the Android/OEM system UI.
- A foreground media-projection service keeps the user-approved session active and shows an ongoing notification with a Stop action.
- The Live screen offers an optional floating Jarvis status panel. You must explicitly enable Android's **Display over other apps** permission; the panel appears when you switch away from Jarvis and provides **Open Jarvis** and **Stop share** controls. It hides when Jarvis returns to the foreground.
- The floating panel is a status/control surface, not a continuous Gemini-style voice/video session or live transcript. **Talk** currently uses Android's one-shot speech recognizer; review the transcript and tap **Ask Jarvis** to send it.
- Jarvis samples downscaled frames locally for preview. Capture pauses while the Jarvis activity is visible to avoid showing a recursive preview.
- Screen frames are sent to the configured AI endpoint only when the user taps **Ask Jarvis**.
- The **+** menu supports:
  - PDF: renders up to 5 pages into images. It does not OCR or extract text from PDF pages in this version.
  - Images: downscaled image attachment.
  - Videos: up to 3 sampled still frames. The full video and its audio are not attached.
  - Plain-text files: locally read up to 80,000 characters.
- File content is loaded locally after the user selects it. It is sent to the configured AI endpoint only after the user taps **Ask**.
- The selected endpoint/model must support OpenAI-compatible `image_url` message content for screen, PDF, image and video-frame queries. Text-only models or servers may reject visual requests.
- This is a visual screen/file context workflow, not continuous Gemini-style two-way live voice/video streaming. Screen content is not continuously uploaded.
- Visual attachments stay in memory for the current app session; the image bytes are not stored in persistent chat history.

## Persistence and privacy

- Server URL, selected model, personality, language and speak-replies preference are saved automatically as they change.
- The API key remains encrypted with AES-GCM using a non-exportable Android Keystore key.
- Conversation text, personal memory and per-app policy settings use app-private SharedPreferences. They are not separately encrypted in this preview.
- Gmail/Google Calendar OAuth is not connected. The app does not have Gmail read/search/reply access in this build. Proactive calendar alerts, accessibility-based typing, in-app tapping, built-in web research tools, background wake-word listening and local LLM inference are not enabled.
- Screen capture requires explicit Android consent for each session. Stop sharing in Jarvis or use Android's system screen-sharing stop control to end it.

## About Jarvis

Jarvis is intended as a personal, voice-first AI companion for learning, conversation, visual explanations and simple phone shortcuts. It connects to the OpenAI-compatible server the user configures, uses Android voice input/output, retains separate user-managed chat and memory data, opens permitted installed apps and can answer questions about user-selected screen/file content. It is not a payment agent, does not currently automate in-app taps or typing, and does not connect to private Google services.

## Build and test

From the repository root:

```bash
cd android
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions runs the same unit-test and debug-APK build tasks. The APK is produced at `android/app/build/outputs/apk/debug/app-debug.apk`. For pull requests, the Android workflow runs `:app:testDebugUnitTest` before `:app:assembleDebug` and uploads the APK as the `openjarvis-android-debug` artifact. A green result applies to the exact PR revision shown by that workflow run.

## Server connection

The app connects to an OpenAI-compatible OpenJarvis endpoint:
- `GET /v1/models` discovers available model IDs.
- `POST /v1/chat/completions` streams assistant replies using Server-Sent Events.
- Requests use `Authorization: Bearer <key>`.
- HTTPS is required; credentials are never placed in URLs.
