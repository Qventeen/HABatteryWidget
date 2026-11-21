# App icon and notification icons — design

- **Date:** 2026-11-21.
- **Status:** approved by the owner (2026-11-21), with the icon rule
  change of §3.2. Owner answers and picks are marked "owner"; the calls I
  made are marked "my call".
- **Design:** canvas rows "App icon · v10" (battery ideas), "Smart-home
  icon · v11" (smart-home ideas, widget family) and "App icon · v12" (the
  pick in three colours, notifications per widget). The repo copy goes to
  `docs/design/` when this is built (§3.4).
- **Set:** `0015`, epic `epic-widget-improvements`. Code on branch
  `0015-app-icon`, from the epic.
- **Builds on:** 0011 (one channel per type, §3.5) and 0012 (mode
  notifications, §3.5). This spec changes their icon rule (§3.2). On one
  topic the newest spec wins.

## 1. Intent (owner, 2026-11-21)

- The app has no icon of its own: the launcher still shows the Android
  Studio template (the robot head on a green grid), also as the themed icon.
- The owner does not like the notification icon. Today it is one of four
  generic Material symbols by type (`report`, `warning`, `check_circle`,
  `info`), so the status bar shows "!" or "i" and nothing says which app
  or what the alert is about.
- The app may grow from a battery widget into a set of smart-home widgets.
  This is a direction, not a plan; the icon must not tie the app to the
  battery alone.
- Picks: v11 idea 1 **House of tiles** (my recommendation; owner OK) in
  colour version **A · Lit window** (owner). Notifications use **one glyph
  per widget** (my recommendation; owner OK).

## 2. Approaches

The canvas compared 20 ideas. In short:

- **A battery mark** (v10, best: 3 Charge ring): fits the app as it is
  today; out of date once other widgets come.
- **A home mark** (v11, picked: 1 House of tiles): a house made of tiles,
  one tile per widget. New widgets fit the idea without a new icon. Clear
  at 18 dp; not like the Home Assistant logo (a circuit in a house).
- **A brand mark** (v11, 9 Lumensync mark): never out of date, but says
  nothing about the home on its own.

## 3. Decisions

### 3.1 Launcher icon

- **Adaptive icon only** (minSdk 26, so every device uses it). The template
  files go: `ic_launcher_background.xml`, `ic_launcher_foreground.xml` and
  the ten `mipmap-*/ic_launcher*.webp` (never used on API 26+; my call).
  `ic_launcher` and `ic_launcher_round` keep their names and point to the
  same three layers.
- **Background:** solid `#2E7D32` (the light palette accent).
- **Foreground:** a white (`#FFFFFF`) house with a 2 × 2 grid of tiles.
  The top-right tile is amber `#FFB869` ("one window lit"); the other three
  tiles are holes that show the green.
- **Monochrome** (themed icons, Android 13+): the same house, all four
  tiles cut out.
- **Geometry**, in a 24-unit glyph box (as drawn on the canvas):
  - House: `M12 3.4 L20.6 10.3 V19.1 A1.8 1.8 0 0 1 18.8 20.9 H5.2
    A1.8 1.8 0 0 1 3.4 19.1 V10.3 Z` (bottom corners radius 1.8), its
    outline grown by 0.5 with round joins, so the roof point and the eaves
    are soft.
  - Tiles: 3.6 × 3.6, corner radius 0.9, at (7.8, 11.2), (12.6, 11.2),
    (7.8, 16), (12.6, 16).
  - On the 108-dp adaptive canvas the glyph is scaled ×2 and moved by
    (30, 30): 48 dp, inside the 66-dp safe zone; 56 % of the visible
    icon.
- Holes are real holes in one path (even-odd fill), not painted green, so
  the monochrome layer and any launcher background work (my call).

### 3.2 Notification icons: one glyph per widget

This changes 0011 §1 and §3.5 ("the type sets the icon") and 0012 §3.5
("the small icon follows the type"). Owner OK on 2026-11-21, with the
recommendation, and confirmed with this spec.

- **Battery alerts** (every `LevelValue` not in `LevelValue.INVERTER`):
  `ic_notify_battery`, the v10 "Level bars" glyph in one colour (a battery
  outline with three of four bars).
- **Inverter alerts** (grid input, output) **and mode changes:**
  `ic_notify_inverter`, the inverter sign (a square with a diagonal, "="
  and "~"), as on the v12 board.
- **Per widget, not per value** (my call): a temperature alert of the
  battery shows the battery glyph. A future widget brings its own glyph
  (the v11 "Widget family" board).
- **Unchanged:** the channel per type, its sound, vibration and pop-up;
  the accent colour (the type colour for levels, the mode colour for
  modes, 0012).
- The four `ic_notify_critical|warning|normal|info` drawables go (unused).
- Both glyphs are 24 dp vectors, white `#FFFFFFFF`, drawn for this app
  (no third-party art, so no licence notes).

### 3.3 What stays

- The app name "HA Battery Widget" (and the widget names). A rename for
  the smart-home direction is a separate decision; the icon does not
  depend on it.
- The in-app icons (tabs, mode icons, buttons).

### 3.4 Design copy

- `docs/design/app-icon.png`: a 2× sheet rendered from the v12 boards
  (version A at big size, light and dark home screen, themed icon,
  notifications per widget), and a README section "App icon" with the
  colours and geometry above.
- Canvas: the v12 board title gets "A picked".

## 4. Catches

- **Launchers cache icons.** After the update the old robot may stay until
  the launcher refreshes; a restart of the launcher or the phone fixes it.
- **The status bar no longer shows the type.** Critical and Warning still
  differ by sound, vibration and pop-up (0011); level alerts also by the
  circle colour in the shade.
- **Mode alerts:** the circle shows the mode colour (0012), so a Critical
  mode alert looks like any other inverter alert until it sounds.

## 5. Tests

- Robolectric (`AndroidLevelNotifierTest`): a battery level alert posts
  with `ic_notify_battery`; a grid-voltage alert and a mode alert post with
  `ic_notify_inverter`. Existing channel, colour and text tests stay.
- Gate: `./gradlew lint testDebugUnitTest assembleRelease
  :app:compileDebugAndroidTestKotlin`.
- Phone check by the owner: the launcher icon (normal and themed), and one
  notification of each kind.

## 6. Non-goals

- No rename, no Play Store artwork, no in-app logo.
- No glyphs for widgets that do not exist yet.
- No per-type badge on the small icon.
