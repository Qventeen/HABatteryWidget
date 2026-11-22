# Ideas for later

Ideas worth keeping that are not specs yet. An idea here is not a promise:
when the owner picks one, it goes through brainstorming into a spec
(`docs/superpowers/specs/`) and leaves this list.

Size is a rough guess: **S** about a day of work, **M** a few days, **L** a
week or more. Status: **idea** (open questions), **ready** (clear enough to
write a spec), **waiting** (needs something first).

## Data and alerts

| Idea | Why | From | Size | Status |
|---|---|---|---|---|
| **Push alerts from Home Assistant**: our own Firebase project or an ntfy server, so an alert arrives in seconds, not after the next sync | Today alerts come only after a sync (15 min or more; later when the phone sleeps) | spec 0017 (idea, with the research) | L | idea |
| **Alerts through the HA Companion app first**: an HA automation sends `notify.mobile_app_<phone>`; no app change | The smallest test of push: shows whether fast alerts are worth building | spec 0017 §3 | S | ready |
| **Sync error notification**: tell the owner when syncs keep failing | A widget's ⚠ is easy to miss for hours | spec 0011 (left out then) | S | idea |
| **Text levels for other HA text entities**: the mode editor's mechanism for any text sensor | Other inverters and devices send other texts | spec 0014 §1 | M | idea |

## Widgets

| Idea | Why | From | Size | Status |
|---|---|---|---|---|
| **More widgets for the smart home**: lights, climate, power, security (glyphs drawn in the canvas section "Widget family") | The app may grow from a battery widget into a set of home widgets | owner, 2026-11-21; canvas "Widget family" | L | idea |
| **"Add widget" with a size choice**: one widget entry per size | The home screen always adds the default 4×2 | spec 0006 | M | idea |
| **Generated picker previews** (Android 15+ `providePreview`) instead of the static preview layout | The picker would show the real widget, always up to date | spec 0001 | M | idea |
| **Icon choice on the battery widget**, reusing the strip's icon picker | The strip has it; the battery widget does not | spec 0012 | M | idea |
| **Strip layout choices**: order, hide a part | Only if the fixed layout still bothers the owner after use | spec 0012 | M | idea |

## Battery profile

| Idea | Why | From | Size | Status |
|---|---|---|---|---|
| **Defaults per battery type for temperatures, SOC and delta** (LTO differs most) | Today an LTO owner edits these levels by hand | spec 0013 | S | idea |

## App and design

| Idea | Why | From | Size | Status |
|---|---|---|---|---|
| **A new app name** for the smart-home direction | "HA Battery Widget" fits only the first widget | spec 0015 §3.3 | S | waiting (for the widget family) |
| **Other app icons**: the battery and smart-home ideas, "Signal home" liked as a variation | Choices if the app grows or is renamed | canvas "Icon ideas" | S | idea |
| **The unlock hint text**: say honestly that the unlock refresh can come late | Android 14+ holds the unlock signal while the app sleeps | spec 0016 §4 | S | waiting (owner's check: unlock after 15+ min idle) |

## Infrastructure and maintenance

| Idea | Why | From | Size | Status |
|---|---|---|---|---|
| **CI pipeline**: build, unit tests and lint on GitHub for each push and pull request | Catches a broken build without a local run | spec 0001 | S | ready |
| **Dependency updates**: Gradle, the Android plugin, Kotlin, AndroidX, OkHttp, Robolectric (lint's 15 "newer version" notes) | Security and bug fixes; small steps stay cheap | lint, 2026-11-22 | S–M | ready |
| **Move to targetSdk 37** following the Android 17 widget memory rules (`references/android-skills/system/glance-appwidget-updates-and-memory/`) | Needed when Play asks for the new target | spec 0001 | M | waiting (Android 17 / Play deadline) |
