# CarfBot

AI chat assistant for Android. Type or speak a request and CarfBot opens the app, contact, song, photo or file for you. It can also be your phone's default digital assistant.

## Build the APK on GitHub (no Android Studio needed)
1. Create a GitHub repo and push this folder to `main`.
2. Open the **Actions** tab. The *Build CarfBot APK* workflow runs and uploads `CarfBot.apk` as an artifact.
3. To publish a release: `git tag v1.0.0 && git push origin v1.0.0`. The APK is attached to the GitHub Release automatically.

## Build locally (Android Studio, or Termux with Gradle + Android SDK)
`gradle assembleRelease` then find `app/build/outputs/apk/release/app-release.apk`.

## Make it your default assistant
Install the APK, open CarfBot, grant permissions, then tap the gear icon, then "Set CarfBot as default assistant" and choose CarfBot under *Digital assistant app*. Long-press Home or the power button to summon it.

## Assistant overlay
When CarfBot is the default assistant, holding the power button (or long-pressing Home) opens a dark "Listening" overlay with an animated orb, live transcript and a glowing edge animation. Haptics fire when it opens, when listening starts, when a reply arrives and when an app, contact, song or file is opened.
On some phones you must also set the power button to open the assistant: Settings > Advanced features > Side key > Press and hold > Wake digital assistant (Samsung), or Settings > System > Gestures > Power menu (Pixel).

## App UI
The app opens with a launch animation: the full-screen "Good Morning" card shrinks into a stack of cards. Tap a card (or pinch out on it) to expand it, and pinch in or press back to return to the stack.
- Good Morning: the assistant chat (voice, typing, swipeable tip notes with action buttons).
- Smart Shortcuts, Apps & Files, Music & Media, Clipboard & Screenshots: tap a note to run that command in the chat.
- Settings: Gemini key, default assistant, overlay test, notification access, double-tap back.

## Overlay greeting
When the assistant opens with no conversation yet, it shows a frosted greeting card ("Let's get started", "I'm Listening") and three glass orbs: files (left), a golden mic orb (centre) and camera (right). Once you speak or type, it switches to the live transcript.

## Icon
The launcher icon is a glowing blue ring on black (adaptive icon, with a monochrome version for themed icons). Source PNGs are in `app/src/main/res/drawable-*/ic_launcher_foreground.png`.

## If the assistant overlay doesn't work
Open CarfBot, tap the gear icon: it shows whether CarfBot is your default assistant, has a button to open the assistant settings, and a "Test assistant overlay" button to check the overlay without the power button. Voice needs the Google app (speech recognizer) and the microphone permission; typing always works.

## Smart features
- Home pills and cards change with the time of day (morning: music, calendar, weather; afternoon: camera, maps, mail; evening: reading, notes, alarm). Swipe a card left or right to dismiss it, or tap its action buttons to run a command.
- Music: "pause", "play", "next song", "previous song" work with any music app. "What's playing" and "lyrics" (for the current song) need notification access (gear icon). "Identify song" uses Google Sound Search or Shazam if installed. Lyrics open in your browser.
- Clipboard: "summarize clipboard", "translate clipboard to Spanish", "organize clipboard", "what's on my clipboard" (CarfBot must be on screen). Screenshots: "summarize my screenshot", "extract text from my screenshot" (copies the text).
- Chaining: "open camera, take a photo and share it on WhatsApp". The camera needs you to press the shutter, and the share sheet needs you to pick the contact.
- Triggers: power-button hold, the corner swipe (turn on "Swipe from corners" in your phone's default assistant settings), and the experimental double-tap on the back (gear icon).

## AI (Gemini, free)
CarfBot uses the Google Gemini API. Get a free key at https://aistudio.google.com/apikey, then open the menu icon in the chat screen (or the gear on the home screen) and paste it. The free tier has per-minute and per-day limits, and Google may use free-tier prompts to improve its products.

## Commands
- `open camera`, `launch spotify`
- `call mom`, `contact john`
- `play <song>` (local music first, then your music app)
- `find photo <name>`, `find video <name>`, `find document <name>`
- `directions to <place>`, `search <anything>`
- Anything else goes to the AI chat (add your free Gemini API key in Settings).

## Notes
- Fonts: uses the system sans font. Drop Inter in `res/font` and edit `Sans` in `Theme.kt` to change it.
- Android hides documents (PDF, DOCX) from apps, so document requests open the system file picker when there's no direct match.
- The release APK is signed with the debug key so it installs directly. Use your own keystore for Play Store.
