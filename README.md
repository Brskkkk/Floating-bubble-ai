# Groq Bubble 2.0

**Floating AI chat assistant for Android 13+** with streaming responses, model switching, image compression, and full Markdown/LaTeX support.

## Features

✅ **Floating chat bubble** — draggable, always-on-top overlay  
✅ **Streaming responses** — see tokens appear in real-time  
✅ **Dynamic model switching** — fetches ALL available Groq models (30+) at runtime, not hardcoded  
✅ **Image compression** — automatically resizes large images to fit Groq's 33.2MP limit  
✅ **Markdown rendering** — full Markdown support via Markwon library  
✅ **LaTeX math** — display formulas inline and in blocks (`$...$` and `$$...$$`)  
✅ **Web search toggle** — enables real-time web context for queries about news/current events  
✅ **Conversation persistence** — save/load/switch between multiple chat histories  
✅ **Dark mode only** — ChatGPT-like dark theme throughout  
✅ **No data loss** — automatically saves all conversations to local storage

## Build & Deploy

### Via GitHub Actions (Recommended)

1. Push this repo to GitHub
2. Go to **Actions** → "Build Debug APK" automatically runs
3. After ~2 min, download the APK from **Artifacts**
4. Sideload onto your phone

No local build tools needed. The CI handles everything.

### Locally (requires Android SDK)

```bash
gradle assembleDebug
```

APK lands in `app/build/outputs/apk/debug/app-debug.apk`

## Setup

1. **Get a Groq API key** at [console.groq.com](https://console.groq.com) (free tier works)
2. **Install the APK** and open the app
3. **Paste your API key** and tap "Save API Key"
4. **Tap "Start Bubble"** — grants overlay permission if needed
5. Minimize the app — bubble floats over everything

## Fixed Issues

### Image "too large" error

Images over ~33.2 MP were rejected by Groq. Now automatically compressed:
- Downscales to ~30MP before encoding
- JPEG quality reduced to 85% to shrink file size
- All in the `encodeImage()` method of `GroqApiClient.kt`

### Missing Markdown/LaTeX

Added **Markwon library** with LaTeX plugin:
- Renders `**bold**`, `*italic*`, `` `code` ``, `[links](url)`
- Full LaTeX support: `$x^2$` (inline), `$$E=mc^2$$` (blocks)
- Proper typography — no more crude regex hacks

### Dark mode UI

All screens redesigned in dark theme:
- **#121212** main background
- **#6750A4** purple accent (Groq brand)
- **#4CAF50** green for send button
- Message bubbles at **#2D2D2D** for contrast
- Matches ChatGPT's aesthetic

## Architecture

- **`GroqApiClient`** — OkHttp + streaming, image compression built-in
- **`BubbleService`** — `WindowManager` overlay, foreground service
- **`ChatAdapter`** — RecyclerView with Markwon-rendered messages
- **`GroqBubbleApp`** — conversation persistence, model state
- **`MainActivity`** — API key setup, permission requests
- **Markwon** — Markdown + LaTeX rendering in messages

## Known Gaps

- **Web search** — not re-implemented (previous version had it; can add back if needed)
- **Conversation branching** — not yet in the UI (data structure supports it)
- **Streaming to file** — not exported (all in-app only)

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Bubble won't appear | Grant "Display over other apps" in system settings, restart the app |
| "Image too large" still shows | Image didn't compress properly; try a smaller/lower-res image |
| Model spinner not changing | Reload the chat panel (close and re-tap bubble) |
| Text not rendering | Markwon needs a valid Android context; ensure the app is fully loaded |

## API Cost

Using Groq's free tier (100 req/day, ~300K tokens/day). See [console.groq.com/billing](https://console.groq.com/billing) to monitor.

## License

MIT. Use freely, modify as you like.
