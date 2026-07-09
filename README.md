# Groq Bubble

Floating AI chat-head overlay for Android 13+, backed by Groq's API. Tap the bubble to
expand a chat window — send text or attach an image (vision model auto-switches). The
AI sees the full running conversation (last 20 messages) each time, so it remembers
what it said earlier in the session. Nothing is persisted to disk — kill the app and
history resets, which is intentional (not a bug).

## Build via GitHub Actions (no PC / no local Gradle needed)

1. Push this whole folder to a new GitHub repo.
2. Go to the **Actions** tab → the "Build Debug APK" workflow runs automatically on
   every push (or trigger it manually via **Run workflow**).
3. When it finishes, open the run → **Artifacts** → download `groq-bubble-debug-apk`.
   Unzip it, sideload `app-debug.apk` onto your phone.

No Gradle Wrapper is committed here — generating `gradle-wrapper.jar` requires either a
local Gradle install or a network fetch, neither of which was available while building
this. Instead, `.github/workflows/build.yml` uses `gradle/actions/setup-gradle` to
install Gradle 8.13 directly on the runner and calls `gradle assembleDebug` (no `./gradlew`
needed). This is an officially documented pattern, not a hack — see the [setup-gradle
docs](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md) if you want to
tweak the Gradle version later.

## Structure
- `MainActivity` — paste your Groq API key, grant permissions, start the service. Also
  catches `ACTION_SEND` share-intents so other apps can share an image straight in.
- `BubbleService` — foreground service drawing the draggable bubble and the expandable
  chat panel (`WindowManager` + `TYPE_APPLICATION_OVERLAY`).
- `GroqApiClient` — OkHttp wrapper around `POST /openai/v1/chat/completions`. Switches
  to a vision model automatically when a message includes an image.
- `ChatAdapter` / `item_message.xml` — RecyclerView chat list.

## Fixed since the last version
- **Panel keyboard was broken.** The expanded chat window previously had
  `FLAG_NOT_FOCUSABLE` effectively set (via a bugged flag expression), so tapping the
  message box could never bring up the keyboard. Fixed: the panel is now focusable
  (the bubble icon itself still isn't, so it doesn't steal focus while collapsed), and
  `softInputMode = SOFT_INPUT_ADJUST_RESIZE` is set so the panel resizes above the keyboard.
- **Android 13 notification permission was never requested.** `POST_NOTIFICATIONS` is a
  runtime permission from API 33 onward; without requesting it, the foreground-service
  notification the bubble depends on to survive may not show. Now requested on launch.
- **Missing `gradle.properties`.** AGP refuses to build AndroidX projects without
  `android.useAndroidX=true` — this file didn't exist before, which would have failed
  the very first build.
- **No Gradle Wrapper / CI path.** Added `.github/workflows/build.yml` (see above).
- Removed dead pre-Android-8 fallback code paths (minSdk is 26, so they never ran anyway).
- History sent to Groq is capped at the last 20 messages so long sessions don't quietly
  blow past context/rate limits — the AI still has working memory of the conversation,
  it just doesn't resend the entire thing forever.

## Known gaps still not implemented (not required for this to build/run)
- **Non-image file attachments** aren't wired to real content — sharing a PDF just drops
  a placeholder string into the chat instead of extracting its text.
- **No streaming** — replies arrive all at once rather than token-by-token.
- **Vision model name may drift.** Groq's vision-capable model lineup changes over time;
  double check the current model string at https://console.groq.com/docs/models if
  image messages start failing.
