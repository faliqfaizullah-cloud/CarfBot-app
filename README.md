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

## Commands
- `open camera`, `launch spotify`
- `call mom`, `contact john`
- `play <song>` (local music first, then your music app)
- `find photo <name>`, `find video <name>`, `find document <name>`
- `directions to <place>`, `search <anything>`
- Anything else goes to the AI chat (add your Anthropic API key in Settings).

## Notes
- Fonts: uses the system sans font. Drop Inter in `res/font` and edit `Sans` in `Theme.kt` to change it.
- Android hides documents (PDF, DOCX) from apps, so document requests open the system file picker when there's no direct match.
- The release APK is signed with the debug key so it installs directly. Use your own keystore for Play Store.
# CarfBot
