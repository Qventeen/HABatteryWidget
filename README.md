# HA Battery Widget

An Android home-screen widget (Jetpack Glance) and a settings screen. They show
a JK-BMS battery read from Home Assistant (HA): state of charge, total voltage, current, power, Ah left, average cell
voltage, cell delta, MOSFET temperature, battery temperatures, balancing. The
app reads HA with a long-lived HA token (see "Token type"). The widget draws from the local cache only.

The battery widget follows design v5 (`docs/design/`): a SOC ring, two big pack
values, cell and temperature pills, time left, and an auto-refresh toggle. A second
widget, the inverter strip, is a one-row strip with the inverter mode, the grid input
voltage and the output voltage, read from the same HA. The app has three tabs:

- **Battery**: a preview of the real widget for each size (4×2, 5×2, 4×3, 5×3),
  "Add to home screen", the "Widget" settings (kWh or Ah under SOC, current or
  power, font sizes), colour levels and alerts, entities and capacity.
- **Inverter**: a preview of the strip for each size (3×1, 4×1, 5×1), "Add to home
  screen", the display (mode as a big icon or icon + word, font sizes), the icons,
  colour levels and alerts for the two voltages, and the inverter entities. Blank
  entities mean the strip is not used.
- **Common**: the theme (system, light, dark), on Android 13+ the app language, the
  HA address and token, auto-refresh, notifications and the home-screen scale.

## The widgets on the home screen

- Add a widget from the launcher's widget list or with "Add to home screen" on its
  tab. The battery widget is added as 4×2; resize it there to 5×2, 4×3 or 5×3. Each
  size has its own layout. The strip is added at 3×1; resize it to 4×1 or 5×1.
- Tap a widget to open the app on its own tab. On the battery widget, ↻ syncs now and
  the toggle turns auto refresh on or off. The strip has neither.
- One sync reads both widgets. Each one is judged alone: when one fails, the other
  still updates. A widget with no entities set shows "set up".
- Inverter mode changes send a notification, like the colour levels with a bell, when
  notifications are on in Common.
- Some launchers draw widgets smaller than their size (HyperOS: about 78 %).
  Set "Home-screen scale" on Common (80–200 %, default 100) to make up for it; about
  128 % on HyperOS. It has its own "Reset scale". The app preview always shows 100 %.
- Font sizes are set per group from 10 to 100 %; 50 % is the design size. A
  text that does not fit its box at the chosen size is shown smaller. "Reset
  sizes" on the Battery tab restores the battery font sizes only, not the scale.

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

`tools/mock_ha.py` (Python 3, standard library only) serves `/api/template`,
`/api/states` and `/api/states/<id>`.
Local testing only — it accepts any token:

```bash
python3 tools/mock_ha.py --port 8123 --soc 55 --power -500 --current -10 \
  --total-v 26.5 --ah-left 157 --capacity 314 --avg-cell 3.31 --delta 0.01 --mosfet 27 \
  --temps "25,23" --balancing on   # add --status 401 to test errors
```

Options you leave out are returned as `null`. For Fill, the mock also lists
`sensor.<prefix>_temperature_sensor_N` for each value of `--temps`
(`--prefix`, default `bps_bms`; another prefix gets an empty list).
`GET /api/states/<id>` answers for the entity ids that Fill makes from
`--prefix`, with the option value as the state (`unknown` if you left the option out);
other ids get 404. `GET /api/states` lists them all. Add `--user-token` to
make `POST /api/template` return 401, like a non-admin token. `--status` wins
over everything. In the app on the Android
emulator, set the HA URL to `http://10.0.2.2:8123` (the emulator's address for
the host) and use any token.

## Token type

Choose the type of your token in the app's token section.

- **Admin**: one `POST {baseUrl}/api/template` request for each sync.
- **User**: a token of a non-admin HA user. The app sends one
  `GET {baseUrl}/api/states/<entity_id>` for each configured entity, at most 4 at
  a time. Fill uses one `GET {baseUrl}/api/states` (30 s timeout).

The app keeps one saved token for each type. "Remove token" clears the token of
the selected type only.

To make a non-admin token:

1. In HA, open Settings, People, and add a person without "Administrator".
2. Log in to HA as that user.
3. Open Profile, Security, Long-lived access tokens, and create a token.

A non-admin token is not read-only: it can still change states in HA. The app
itself only reads.

## Power sign in HA

The **current** sign sets the state: negative = discharging, positive =
charging, |I| <= 0.1 A = idle. Power is shown as an absolute value, so its sign
does not matter. This matches a JK-BMS read through ESPHome (checked on the
owner's BMS, 2026-11-15); another BMS integration may use the opposite sign.

## Languages

English (default) and Ukrainian. On Android 13+ you can set the app language
in the app ("Appearance") or in the system settings. The widget follows it,
also when the app is closed.

## License

Copyright 2026 the HA Battery Widget authors.

Licensed under the [Apache License, Version 2.0](LICENSE), the same license as
Home Assistant Core and the Home Assistant Android app. You may use, change and
share this code under its terms.

This is an independent project. It is not affiliated with or endorsed by Home
Assistant or the Open Home Foundation. "Home Assistant" is their trademark.
