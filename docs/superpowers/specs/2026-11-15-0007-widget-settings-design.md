# Widget settings — design

- **Date:** 2026-11-15
- **Status:** draft, waiting for the owner's approval.
- **Set:** `0007`. Code on branch `0007-widget-settings`, from `develop` after
  branch `0004-widget-design` (design v5 and these specs) is merged into
  it (the owner approves that merge).
- **Builds on:** spec 0005 (`FontPercents` 25–100 %, 50 % = design) and spec 0006 (the
  preview in the app).

## 1. Intent

The owner (2026-11-15): remove the font-size sliders and use number fields
with Reset and Save buttons at the end; move the Widget section to the top
of the settings, above Connection. The widget settings are what people
change most after the first setup.

## 2. Goals, non-goals, anti-goals

**Goals**

- The Widget section is the first settings section.
- Display choices and five font fields in percent, then Reset and Save.
- Wrong input shows a field error and is not saved.
- The preview (0006) shows the typed values before Save.

**Non-goals**

- Fields per widget size; theme and language (0008).

**Anti-goals**

- Text that overflows at a saved value (0005's fit rule prevents it: a
  text grows only as far as its box allows).
- Typed values lost on rotation (the other sections keep drafts with
  `rememberSaveable`).

## 3. Owner decisions (2026-11-15)

1. Number fields, not sliders; Reset and Save at the end of the section.
2. The Widget section moves above Connection.
3. Values in percent, one value per group for all sizes (Claude's
   proposal; "all picks").
4. 50 % = the design size, 100 % = twice the design size; text can be made
   bigger and smaller; Reset returns to the default 50 % (owner).

## 4. Approaches (Claude's calls)

**A. What Save covers**

- **A1 (chosen):** one Save for the whole section (display choices and
  fonts), like the Refresh section, where the switch and the interval are
  saved together.
- A2: display choices save at once, fonts with Save: two behaviours in one
  section.

**B. Reset**

- **B1 (chosen):** fills the five fields with 50 (the design size); saved
  with Save.
- B2: Reset saves at once: a surprise after the owner asked for Save.

## 5. Design

### 5.1 Screen order

The preview (0006) and the actions row stay on top. Then: **Widget**,
Connection, Token, Entities, Refresh, Notification, Capacity.

### 5.2 Widget section

```
Widget
  Under SOC        ( kWh | Ah )
  Second value     ( Current | Power )
  Font sizes, %  (50 = design size)
  SOC number            [ 50 ] %   25–100
  kWh / Ah under SOC    [ 50 ] %   25–100
  Main values           [ 50 ] %   25–100
  Cell values           [ 50 ] %   25–100
  Small text            [ 50 ] %   25–100
                          [ Reset ]  [ Save ]
```

- Display choices: segmented buttons, as today.
- Font fields: whole numbers, number keyboard, "%" suffix, the range as
  supporting text.
- Reset (outlined button), Save (the existing `SaveButton`).
- Summary when folded: "kWh · current", plus "· text size changed" when
  any field is not 50.

### 5.3 Validation and saving

- Each field: a whole number 25–100; else the field error "Enter a number
  from 25 to 100". Empty is an error too.
- Save stores display choices and fonts together, only when every field is
  valid (as the other sections), then redraws the widgets.

### 5.4 Live preview

The preview (0006) uses the draft: valid fields and the chosen display
choices; an invalid field uses the saved value. It goes through 0006's
`PreviewWidget`, which is given the draft settings. The home-screen
widgets change only after Save.

### 5.5 Strings

New texts in `values/` and `values-uk/` in the same commit: labels, the
range text, the field error, Reset, the summary. Removed: the slider texts.

## 6. Tests

- **Unit:** validation (24, 25, 50, 100, 101, empty, "abc", "85.5"); the
  save action stores both parts; Reset values.
- **ViewModel:** save; drafts survive recreation.
- **androidTest:** Widget is the first section; fields show the saved
  values; Reset fills 50; an error blocks Save; Save stores; the preview
  follows the draft.
- **Done gate:** `./gradlew lint testDebugUnitTest assembleRelease`;
  instrumented tests on `Pixel_8_API_35`.

## 7. Left to the builder

Spacing, the exact field component, where the field errors sit, and the
Ukrainian wording.
