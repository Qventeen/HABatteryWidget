# HA Battery Widget

An Android home-screen widget (Jetpack Glance) and a settings screen. They show
a JK-BMS battery read from Home Assistant (HA): state of charge, total voltage, current, power, Ah left, average cell
voltage, cell delta, MOSFET temperature, battery temperatures, balancing. The
app calls `POST {baseUrl}/api/template` with a
long-lived HA token. The widget draws from the local cache only.

The widget follows design v5 (`docs/design/`): a SOC ring, two big pack values,
cell and temperature pills, time left, and an auto-refresh toggle. The app
shows a preview of the real widget for each size (4×2, 5×2, 4×3, 5×3). The
"Widget" settings section picks kWh or Ah under SOC, current or power, the
home-screen scale and the font sizes.
The "Appearance" section sets the theme (system, light, dark) and, on
Android 13+, the app language.

## The widget on the home screen

- Add it from the launcher's widget list or with "Add widget" in the app. The
  home screen adds it as 4×2; resize it there to 5×2, 4×3 or 5×3. Each size has
  its own layout.
- Tap the widget to open the app on that widget's size tab. ↻ syncs now; the
  toggle turns auto refresh on or off.
- Some launchers draw widgets smaller than their size (HyperOS: about 78 %).
  Set "Home-screen scale" (80–200 %, default 100) to make up for it; about
  128 % on HyperOS. The app preview always shows 100 %.
- Font sizes are set per group from 10 to 100 %; 50 % is the design size. A
  text that does not fit its box at the chosen size is shown smaller. "Reset
  sizes" restores the font sizes and the scale.

## Requirements

- JDK 17
- Android SDK (`ANDROID_HOME` or `~/Android/Sdk`)

## Build and test

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # any JDK 17 path
./gradlew :app:testDebugUnitTest                # unit tests
./gradlew lint testDebugUnitTest assembleRelease  # full check
```

Instrumented tests run on an emulator. They wipe the app's data, and Gradle
uninstalls the app afterwards, on every attached device. With a phone
attached, name the emulator:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

## Release key

`assembleRelease` needs a signing key. Create it once:

```bash
keytool -genkeypair -v -keystore ~/keys/habattery-release.jks \
  -alias habattery -keyalg RSA -keysize 4096 -validity 10000
```

Add these lines to `~/.gradle/gradle.properties` (never to the repo):

```properties
habattery.release.storeFile=/path/to/habattery-release.jks
habattery.release.storePassword=<store-password>
habattery.release.keyAlias=habattery
habattery.release.keyPassword=<key-password>
```

CI can use the env variables `HABATTERY_RELEASE_STORE_FILE`,
`HABATTERY_RELEASE_STORE_PASSWORD`, `HABATTERY_RELEASE_KEY_ALIAS` and
`HABATTERY_RELEASE_KEY_PASSWORD` instead.

Keep a backup of the key file and its passwords. If you lose the key, you
cannot update installed apps.

## Try it with the mock HA

`tools/mock_ha.py` (Python 3, standard library only) serves `/api/template`.
Local testing only — it accepts any token:

```bash
python3 tools/mock_ha.py --port 8123 --soc 55 --power -500 --current -10 \
  --total-v 26.5 --ah-left 157 --capacity 314 --avg-cell 3.31 --delta 0.01 --mosfet 27 \
  --temps "25,23" --balancing on   # add --status 401 to test errors
```

Options you leave out are returned as `null`. For Fill, the mock also lists
`sensor.<prefix>_temperature_sensor_N` for each value of `--temps`
(`--prefix`, default `bps_bms`; another prefix gets an empty list). In the app on the Android
emulator, set the HA URL to `http://10.0.2.2:8123` (the emulator's address for
the host) and use any token.

## Power sign in HA

The **current** sign sets the state: negative = discharging, positive =
charging, |I| <= 0.1 A = idle. Power is shown as an absolute value, so its sign
does not matter. This matches a JK-BMS read through ESPHome (checked on the
owner's BMS, 2026-11-15); another BMS integration may use the opposite sign.

## Languages

English (default) and Ukrainian. On Android 13+ you can set the app language
in the app ("Appearance") or in the system settings. The widget follows it,
also when the app is closed.
