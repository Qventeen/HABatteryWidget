# Inverter widget — concept

- **Date:** 2026-11-18
- **Status:** concept. Not approved. It needs a detailing session before a
  plan is written.
- **Set:** `0012`, epic `epic-widget-improvements`. Code later on branch
  `0012-inverter-widget`, from the epic.
- **Builds on:** 0009 (both connection ways must fetch the new values), 0010
  (colour levels for the inverter values), 0005 and 0006 (layouts, app
  preview). On one topic the newest spec wins.
- **Design:** new rows on the design canvas (v6). It is an extension: the
  existing boards and the v5 battery widget stay unchanged.

## 1. Intent (owner, 2026-11-18)

Show the inverter on the home screen: its current state or operating mode,
the grid input voltage, and the output voltage that goes to the house. It is
a strip, 4×1 or 5×1 (vertical 1×3 or 1×4 was also named), placed above or
below the battery widget. It is a separate widget, not physically part of
the battery widget, but it is set up and placed from the same app and gets
its data from the same Home Assistant sync. No new app. Its values get
colours by levels (for example by voltage), the same way as in 0010.

## 2. Today

- One widget type: `BatteryWidgetReceiver` with `battery_widget_info.xml`
  (minimum = the 4×2 bucket, 261 × 126 dp, resizable). `MainActivity` is its
  configure activity.
- One sync path: `SyncBattery` → `BatterySource.fetch` → `BatteryReading` →
  `ReadingCache` → `WidgetRefresher.refreshAll()`. Today `refreshAll` redraws
  only `BatteryWidget`.
- Admin token: one template (`TemplateBuilder`) for all battery entities.
  User token (0009): one `GET /api/states/<id>` per entity.

## 3. Proposed shape (a proposal, not decided)

- **A second widget type:** `InverterWidgetReceiver` with its own
  `inverter_widget_info.xml` (minimum 4×1, horizontal resize) and its own
  preview. The launcher lists it as a second widget of the app. Class names
  are fixed once shipped (home screens store them).
- **One sync for both:** the same run fetches battery and inverter values.
  Admin: more lines in the same template (still 1 request). User: about 3
  more GETs. `refreshAll` redraws both widget types.
- **New settings:** inverter entities for the mode (a text state), grid
  input voltage and output voltage; sensor search (Fill) like for the
  battery if possible.
- **Stored reading:** a new `InverterReading` next to `BatteryReading` in the
  cache, so the battery widget code is not touched.
- **Colours:** 0010 bands for the two voltages. For the mode, a colour per
  mode text (for example "Line" green, "Battery" amber, "Fault" red): a text
  → colour map, not number bands.
- **App:** an "Inverter" section for its entities; the preview card gets a
  tab for the strip.
- **Each widget works alone:** a strip placed without inverter entities
  shows "set up", as the battery widget does.

## 4. Catches

- The mode is text, and its values depend on the inverter's HA integration
  (not known yet). Long texts must fit a 4×1 strip.
- One cell row is short. Fonts must fit (`TextFit` exists), and the launcher
  size quirks apply (some launchers draw widgets smaller; home-screen scale,
  spec 0007).
- Partial failure: the battery is fine but an inverter sensor is missing →
  only that part shows "no value".
- The user-token path makes more requests per sync.

## 5. Open questions (for the detailing session)

1. Which inverter and which HA integration? The exact entities for the mode,
   grid input voltage and output voltage; more values (output power, load %,
   grid frequency)?
2. Sizes: 4×1 and 5×1 only, or also vertical 1×3 and 1×4?
3. Mode text: show the HA text as is, or map it to own short words
   (translated)?
4. Mode colours: which modes, which colours?
5. Default voltage levels (for example < 200 V red, 200–210 V amber,
   210–245 V green, > 250 V red)?
6. What does a tap on the strip do: open the app at the inverter section, or
   refresh?
7. ↻ and the auto-refresh toggle on the strip too, or only on the battery
   widget?
8. Should a mode change (grid loss) notify? See 0011, question 2.

## 6. Non-goals

- No change to the battery widget's layouts or its design boards.
- No second app and no second sync schedule.
