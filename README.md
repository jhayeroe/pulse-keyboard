# Pulse Keyboard — Android Alpha (0.3)

An **offline, one-handed, 3×4 Android keyboard** for Filipino/English typing.

## Current alpha

- Native Android input method (IME), no internet or account needed.
- **Classic ABC multi-tap is the default**: press 6 once for M, twice for N, three times for O. T9 predictive typing is optional, and the last selected mode is remembered.
- Three suggestions with an offline English/Tagalog starter dictionary.
- User-added personal words are stored **locally**; no automatic harvesting of typed text.
- Left or right alignment (default right), adjustable width (60–100%) and compact key height (38–66dp, default 44dp).
- Compact layout, 3×4 keypad: 1–9, * = Backspace, 0 = Space, # = Enter.
- Bottom controls: 123 | ← | →; right arrow confirms an active multi-tap letter.
- Shift, numeric mode, symbols, simple emoji/recent choices, and manual clipboard paste.
- A dedicated 🌐 switcher on the keyboard and **Switch Keyboard** in Settings open Android's input-method picker to return to your old keyboard.
- Password fields suppress suggestions, word saving, and clipboard access.
- Theme follows Android light/dark mode.

## Get the APK

1. Open **[Releases](https://github.com/jhayeroe/pulse-keyboard/releases)** and select the newest **Alpha Build**.
2. Download **PulseKeyboard-alpha.apk** to Android and install (allow this install source if Android requests it).
3. Open **Pulse Keyboard**, choose **Enable Keyboard**, enable Pulse Keyboard in Android settings, then press **Choose Keyboard** and select it.
4. Use **Keyboard Settings** to adjust keypad width, height, and side.

This is a testing build, **not yet Play Store ready**. Debug APKs can use different signing keys between CI runs; uninstall an earlier alpha first if Android reports a signature conflict on updating. Type passwords only after testing on your phone.

## Build and test

GitHub Actions → **Pulse Keyboard APK** runs automatically on updates to main or manually through **Run workflow**. It:

1. Sets up Java 17, Android SDK 35 and Gradle 8.9.
2. Runs JUnit tests and Android lint.
3. Builds the debug APK.
4. Uploads a downloadable PulseKeyboard-alpha-debug-apk artifact.
5. On main, publishes the APK as a GitHub **pre-release**.

On a machine with Java 17, Gradle 8.9 and Android SDK 35:

    gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk.

## Privacy

No INTERNET permission, tracking SDK, or background network calls. Personal dictionary uses private Android SharedPreferences. This is a keyboard and can read the text you enter into fields where it is enabled; review the source before trusting it with sensitive data.

## Planned follow-ups

Real-device testing, richer Filipino dictionary, more advanced emoji/clipboard UI, accessibility refinements, and stable release signing.

## Typing example

**T9 mode**: To write **Ano na pare koy**, press `266`, Space, `62`, Space, `7273`, Space, `569` (then Space). Tap one number for each letter. The T9 dictionary prioritizes these Tagalog words.

**ABC multi-tap**: Tap key `6` twice quickly for `n`, then press the right-arrow key (→) to confirm before using key `6` three times for `o`. You can also pause for 1.2 seconds instead of pressing →. Unlike the first alpha, cycling replaces the already committed last letter, for better compatibility with chat editors. Time window: 1.8 seconds.

**Updating from Alpha 0.1:** GitHub builds may use different debug signing certificates. If Android says **App not installed** when updating, select another keyboard in Android Settings, uninstall the old Pulse Keyboard app, then install this build. Your locally saved personal words and layout settings are reset when uninstalling.

## Alpha 0.3 typing fix

The keyboard now selects **ABC multi-tap** on first launch and once when upgrading from an earlier alpha. After that, it remembers the mode you choose. In ABC mode, each rapid press on the same digit **replaces** the prior letter, rather than appending another. The editor receives a committed letter plus an atomic delete-and-replace within a batch edit; the old composing-span-only approach was unreliable in some chat applications. Phone testing is still required.
