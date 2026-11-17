# Commands

Use JDK 17 for every Gradle run (the default `java` here is newer):
`export JAVA_HOME=/usr/lib/jvm/java-17-openjdk`

| Task | Command |
|------|---------|
| Unit tests | `./gradlew :app:testDebugUnitTest` |
| One test class | `./gradlew :app:testDebugUnitTest --tests "<pattern>"` |
| Done gate | `./gradlew lint testDebugUnitTest assembleRelease :app:compileDebugAndroidTestKotlin` |
| Instrumented tests (emulator up) | `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` |

- `connectedDebugAndroidTest` runs on every attached device, wipes the app's
  data (`MainScreenTest`) and uninstalls the app afterwards. The owner's phone
  may be on adb with the real app, token and widgets: always set
  `ANDROID_SERIAL` to the emulator and use `adb -s <serial>`. Never run
  connected tests or `adb uninstall` on the phone.

- Robolectric runs at SDK 34 (`app/src/test/resources/robolectric.properties`);
  JDK 17 cannot run SDK 35+.
- Without the release key properties (`security.md`), `assembleRelease`
  builds an unsigned APK.

## Emulator

- SDK: `~/Android/Sdk` (`emulator/`, `platform-tools/`), shared with Android
  Studio.
- AVDs: `Pixel_8_API_35` (main; API 33+ is needed for the notification
  permission) and `Pixel_6` (API 31, older target). `Pixel_8_API_37` crashes in
  surfaceflinger and restarts system_server: do not use it.
- Headless start: `emulator -avd Pixel_8_API_35 -no-window -no-audio`, then
  `adb wait-for-device` and wait until `adb shell getprop sys.boot_completed`
  prints `1`.
- Run one emulator at a time. Shut it down after use (`adb emu kill`).

## Mock Home Assistant

- `python3 tools/mock_ha.py --port 8123 --soc 55 --power -500 --temps 25,23 --balancing on`
  (all options in README; `--status 401` returns an error).
- In the app on the emulator use `http://10.0.2.2:8123` and any dummy token.
