# Temperatures — design

- **Date:** 2026-11-15
- **Status:** approved by the owner (2026-11-15); built on branch `0004-temperatures`, merged into `epic-widget-v5`.
- **Set:** `0004`. Code on branch `0004-temperatures`, from `develop` after
  branch `0004-widget-design` (design v5 and these specs) is merged into
  it (the owner approves that merge).
- **Builds on:** spec 0002 (data and settings) and spec 0003 (widget v4,
  merged in `d1cf5fc`). Visual rules: design v5, `docs/design/README.md`.
- **Topic set:** 0004 temperatures → 0005 widget layouts v5 → 0006 widget
  preview in the app → 0007 widget settings; 0008 theme and language is
  independent. Each spec can be built and used on its own, in this order.

## 1. Intent

The owner (2026-11-15): "we lost one temperature from design v4". Design v3
always showed the highest and lowest of all battery sensors. v4 shows
highest and lowest only with two or more configured sensors, and Fill adds
only `sensor.<p>_temperature_sensor_1` (`domain/EntityFill.kt`), so after
Fill the second sensor is missing.

The owner's new rule: always two temperature pills, **the average of all
battery sensors** and **MOSFET**. The battery pill's colour follows the
**worst** sensor, so one hot or cold sensor is not hidden behind a normal
average. **Fill finds every battery sensor** the BMS has.

## 2. Goals, non-goals, anti-goals

**Goals**

- The battery pill shows the average of the configured sensors that have a
  value, in whole °C; its colour is the worst level of those sensors.
- Fill asks Home Assistant once for every `sensor.<p>_temperature_sensor_N`
  and puts them in the list.
- Works on today's v4 widget and app card: one battery pill, no ↑ / ↓ pills,
  no empty arrow slot.

**Non-goals**

- The v5 layouts (spec 0005).
- Showing each sensor, user-set limits, MOSFET changes.
- Finding sensors at sync time (only when the user taps Fill).

**Anti-goals** (a failure even if it works)

- The pill looks normal while one sensor is past a limit.
- Fill leaves the temperature list empty or changes saved settings without
  Save.
- Anything is written to Home Assistant.

## 3. Owner decisions (2026-11-15)

1. Two temperature pills, always: battery average, then MOSFET (last). The
   highest / lowest pills go.
2. The battery pill's colour comes from the worst sensor (owner took
   Claude's pick: "all picks").
3. Fill adds every battery temperature sensor Home Assistant has ("all
   picks").
4. Temperature pill layout (design v5): icon, 3–4 dp space, value; no arrow
   slot.

## 4. Approaches (Claude's calls)

**A. Where the average is computed**

- **A1 (chosen):** in `WidgetModel` (domain), from
  `BatteryReading.temperaturesC`. The per-sensor values are needed for the
  worst level anyway, and the rule stays in one tested place for the widget
  and the app card.
- A2: a Home Assistant template computes the average: the per-sensor values
  for the worst level are lost.

**B. Finding the sensors**

- **B1 (chosen):** one extra `POST /api/template` that lists the entity ids
  matching `sensor.<p>_temperature_sensor_<digits>`. Same URL and token as
  the sync; nothing new to configure.
- B2: add a fixed list (`_1` … `_4`): guesses; ids that do not exist show
  "—" and pull nothing into the average, but they clutter the list.
- B3: `GET /api/states`: returns every entity (large), and a second
  endpoint to support.

**C. When Fill asks**

- **C1 (chosen):** Fill stays a draft action in the Entities section. It
  fills every id at once (as today); then the ViewModel asks Home Assistant
  and puts the found sensors into the draft. Nothing is saved until Save,
  as today.
- C2: discover at every sync: changes settings without the user.

## 5. Design

### 5.1 Model (`domain/WidgetModel.kt`)

`Temperatures` becomes:

- `None` — no battery sensor configured (the list is empty): no battery
  pill.
- `Battery(celsius: Double?, level: Level)`:
  - `celsius` = the mean of the configured sensors' values that are not
    null; null when none has a value ("—", plain level).
  - Show rule (as today, `c7ee423`): whole °C, rounded after averaging.
  - `level` = the worst `WidgetLevels.battery` over each sensor's own
    rounded value (BAD > WARN > PLAIN). Example: 20 °C and 50 °C → shows
    35 °C in amber; 20 °C and 60 °C → 40 °C in red; −1 °C and 20 °C →
    10 °C in red.

`One` and `MaxMin` are removed. One configured sensor gives the same result
as today's `One`.

The pill's content description: "Battery temperature 25 °C" with one
sensor; "Average battery temperature 35 °C" with two or more. When the
colour comes from a sensor past a limit, the description adds the level
the same way the other pills do today (if they do not, nothing is added;
builder checks).

### 5.2 Widget and app card (v4 layout, until spec 0005)

- Right column: average cell, delta, the battery pill (not for `None`),
  MOSFET. So 4 pills, or 3 with no battery sensor.
- Battery and MOSFET pills: icon, 3 dp space, value. The 10 dp arrow slot
  and the ↑ / ↓ icons go (they lined up numbers between arrow and no-arrow
  pills; no arrows remain).
- The column's spacing stays as in v4; spec 0005 replaces the layout.
- The app's overview card (Compose) follows the same model.

### 5.3 Finding the sensors (`data/ha/`)

- New method on the existing port:
  `BatterySource.listTemperatureSensors(connection, prefix, token): SensorList`
  with `SensorList = Found(ids: List<String>) | Failed(error: SyncError)`.
  (A port method, not a new interface: same source, same fake in tests.)
- Template (one request to `/api/template`): every `sensor.*` entity id
  that fully matches `sensor\.<p>_temperature_sensor_[0-9]+`, as a JSON list.
- Kotlin sorts the ids by their number (1, 2, 10), keeps the first
  `EntitySettings.MAX_TEMPERATURES` (8).
- The prefix goes into the template only when it has HA object-id
  characters (`a–z`, `0–9`, `_`); otherwise no request is made (result:
  `Found(emptyList())`). This also keeps user text out of the template code.
- Errors map to `SyncError` like the sync (unreachable, token rejected, …).

### 5.4 Fill (Entities section)

1. Tap Fill: every id is filled from the prefix at once (`EntityFill`, as
   today), the temperature list = `[sensor.<p>_temperature_sensor_1]`.
2. When a URL and a token are saved, the ViewModel asks for the sensor
   list. Fill shows progress and is disabled while it runs.
   - Found ≥ 1: the draft list = the found ids.
   - Found 0: the list keeps `_1`; a note under the list: "No temperature
     sensors found for this prefix".
   - Failed: the list keeps `_1`; a note: "Could not read the sensor list:
     <short error>".
   - No URL or token saved: no request, no note (as today).
3. If the prefix in the draft changed while the request ran, the result is
   dropped.
4. Nothing is saved until Save. The user can still edit the list.

### 5.5 Strings, stored data, tools

- New texts (English and Ukrainian, same commit): the two notes, the
  average content description. Removed: highest / lowest descriptions and
  any string no longer used.
- Stored data: unchanged (the temperature list as today).
- `tools/mock_ha.py`: answers the sensor-list template with
  `sensor.<p>_temperature_sensor_N` for each value of `--temps` (and an
  empty list for another prefix).

## 6. Tests

- **Domain (`WidgetModelTest`):** average of 1, 2, 3 sensors; a missing
  value is left out; all missing → null and PLAIN; the worst level (the
  three examples in 5.1); rounding after averaging; level edges per sensor
  (5 / 45 / 0 / 55 °C); `None` for an empty list.
- **Data:** the template contains the prefix pattern; the response parser
  sorts 1, 2, 10, drops non-matching ids, cuts at 8; an invalid prefix makes
  no request; errors map like the sync.
- **ViewModel:** Fill with found / none / failed / no token; the prefix
  changed during the request → result dropped; Save stores the list.
- **Widget (Glance unit tests):** 4 pills (3 with no sensor), no ↑ / ↓, the
  content descriptions.
- **Manual:** `tools/mock_ha.py --temps 25,23,30` → Fill lists three
  sensors; the widget and the app card show 26 °C; `--temps 20,50` shows
  35 °C in amber.
- **Done gate:** `./gradlew lint testDebugUnitTest assembleRelease`;
  instrumented tests on `Pixel_8_API_35`.

## 7. Left to the builder

The exact template text and escaping, the progress look on Fill, the
Ukrainian wording, and where the notes sit in the Entities section.

## 8. Build notes (2026-11-15)

- `Temperatures.Battery` has a `sensors: Int` field (the number of
  configured sensors), so the content description can tell one sensor from
  several.
- The pill's content description is the icon label only ("Battery
  temperature" / "Average battery temperature"). The value is read from its
  own text, as with the other pills, and no level is added because no pill
  adds one.
