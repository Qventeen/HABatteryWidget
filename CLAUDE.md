# HA Battery Widget — project guide

Android home-screen widget (Jetpack Glance) plus a settings screen. It shows a
JK-BMS battery read from Home Assistant through `POST /api/template`.

## Status: clean rewrite in progress

- Work happens on `develop`. The spec is
  `docs/superpowers/specs/2026-11-14-0001-clean-rewrite-design.md`.
  **Read it before you change code.**
- The code in `app/` today is legacy. Do not copy or extend its patterns
  (three fetch paths, AlarmManager, hard-coded Ukrainian text).

## Build and test

- Use JDK 17. The default `java` on this machine is newer:
  `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`
- `gradlew` has no exec bit in git yet, so call it through bash:
  - unit tests: `bash ./gradlew :app:testDebugUnitTest`
  - done gate: `bash ./gradlew lint testDebugUnitTest assembleRelease`
- Release signing uses the local, git-ignored `app/release.keystore`.
  Never commit signing keys, tokens or other secrets.

## Emulator

- Main: `Pixel_8_API_37` (Android 17). Older: `Pixel_6` (API 31).
- SDK: `~/Android/Sdk`, shared with Android Studio.
- Headless start: `emulator -avd Pixel_8_API_37 -no-window -no-audio`.
  Shut it down after use (`adb emu kill`).

## References

`references/` holds 7 cloned expert repos (index: `references/README.md`).
Check them before you design widget, sync or test code. Read only: never
build or run code there. Take the pattern, not the shape (no Hilt, Room or
multi-module here).

## Rules for new code (details in the spec)

- One `:app` module, manual DI. `domain/` is pure Kotlin, no `android.*`.
- KISS: add an interface only when it has a second implementation or a test fake.
- Every visible text lives in `res/values/strings.xml` (English) with
  translations in `values-uk/`. No user text in Kotlin; no logic on translated text.
- One sync path (`SyncBattery`). The widget renders from the cache only;
  no network calls in `provideGlance`.
- The HA token goes only through `TokenStore` (Keystore-encrypted). Never log
  it, and never put it in errors or UI state.
- Code, comments and docs are in English.

## Library docs

Use Context7 when it is connected; otherwise use developer.android.com.
