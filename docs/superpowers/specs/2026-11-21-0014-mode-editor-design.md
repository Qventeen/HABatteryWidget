# Mode editor (inverter mode texts, names and alerts) — design

- **Date:** 2026-11-21
- **Status:** concept. The direction is agreed by the owner (2026-11-21);
  NOT approved for a plan. It needs a detailing session (the open questions
  in §4) after 0013 is built.
- **Set:** `0014`, epic `epic-widget-improvements`. Code later on branch
  `0014-mode-editor`, from the epic. Build order: after 0013.
- **Builds on:** 0012 (the mode table, `ModeAlerts`, the icon slots, the
  Inverter tab), 0010 and 0011 (the level editor parts: type menu, chips;
  one notification channel per type) and spec 0001 (§12 strings, §14
  stored data). On one topic the newest spec wins.

## 1. Intent (owner, 2026-11-21)

- Different inverters send different mode texts. Colleagues with other
  inverters must be able to set which texts the mode entity returns, and
  map them to the modes the app shows.
- The defaults stay as today: the ESP32 texts of 0012 §2.1.
- The user can rename the shown mode names.
- The user can turn the mode notification on or off per mode and set its
  type, in a way close to the level editor.
- The owner also floated a changeable number of modes ("only the ones we
  care about") and called it debatable (see §3, "Fewer modes").

## 2. Today (after 0012)

- The mode text is matched against a fixed table in code (`InverterMode`),
  trimmed and ignoring case: 7 rows (Power On, Standby, Mains, Off-Grid,
  Bypass, Charging, Fault) plus "other" for any text not in the table.
- Each row has a word (EN, UK), a colour, a default icon and a type
  (notification channel). The icon of each row can already be changed
  (`IconSlot.MODE_*`, Icons section on the Inverter tab).
- `ModeAlerts`: any change of the raw text posts a notification when the
  main Notifications switch is on; the first value is only stored;
  `unavailable`, `unknown` and blank are never a mode.
- 0012 §3.5 and §6: the table and the mode alerts are not configurable.

## 3. Direction (to detail)

Recommended shape (option A from the owner talk):

- **Fixed mode kinds:** the 7 rows plus "other". Each kind keeps its
  colour, default icon and default type from 0012.
- **Per kind, stored by kind name** (string keys; an unknown name falls back
  to the default, like the icon choices; no migration, 0001 §14):
  - **Match texts:** a list of texts; the default is the 0012 ESP32 text.
    Matched trimmed and ignoring case. A text belongs to one kind at most;
    the editor shows an error on a duplicate.
  - **Shown name:** the user's own text; blank means the translated default
    word (0001 §12). A user's own name is shown as typed.
  - **Bell** on or off, and the **type** (Critical, Warning, Normal, Info:
    the 0011 channels).
  - **Icon:** as today.
- **Domain:** one matcher turns the raw text into a kind. The widget model,
  the strip and the alerts all use the kind.
- **Mode alert compares kinds, not raw texts:** two texts of the same kind
  (for example `Line` and `Utility`, both grid) are no change. For "other",
  any new raw text is still a change, as today.
- **UI:** a "Modes" section on the Inverter tab: one row per kind (icon and
  name, bell switch, type menu) that opens to its match texts and name, and
  "Reset to default" per kind. It reuses the level editor parts.
- **Fewer modes:** the strip always shows the current mode, so a kind
  cannot be hidden from it. "Fewer modes" means: bell off, or map the texts
  to another kind (for example Bypass shown as grid). A possible extra:
  hide unused kinds in the editor only.

Other options from the same talk:

| Option | Cost (a guess) | Trade-off |
|---|---|---|
| B. Fully custom mode list (add and delete kinds) | ~4–6 h | Most flexible; the icon slots and the stored settings change shape |
| C. A Home Assistant template sensor maps the texts | 0 in the app | Every user must write a template |
| D. Presets per inverter model | small, on top of A | Needs real texts from real inverters |

Estimate for A with the alerts: about 1.5–2.5 h of agent work with
reviews (a guess, from 0012 Task 6 and the Icons section of Task 12).

## 4. Open questions

1. Notify only on entering a mode (as levels do), or also on leaving it?
2. Default bells: all on (today), or some off (for example Power On,
   Charging)?
3. Should the mode colour be editable too (0010 palette)?
4. Hide unused kinds in the editor: yes or no?
5. Evidence: the mode texts of a colleague's inverter (a screenshot of the
   entity's states in Home Assistant), for the defaults and for presets
   (option D).
6. Can the user add a ninth kind? My recommendation: no (that is option B).
7. "Other": can its name and bell be edited, and what are its defaults?

## 5. Non-goals

- No change to the strip layout.
- No custom number of kinds (option B), unless the detailing decides it.
