# Battery profile (type and cells) — design

- **Date:** 2026-11-20
- **Status:** concept, NOT approved. It needs a detailing session (research
  of the presets and the open questions in §4) before a plan.
- **Set:** `0013`, epic `epic-widget-improvements`. Code later on branch
  `0013-battery-profile`, from the epic. Build order (owner, 2026-11-21):
  next after 0012, before 0014.
- **Builds on:** 0010 (value colours: default levels, `DefaultLevels`,
  the "Levels and alerts" section, named "Colours" before 0011), 0012 (the
  app tabs: the Capacity section is on the Battery tab) and spec 0001 (§14
  stored data). On one topic the newest spec wins.

## 1. Intent (owner, 2026-11-20)

The widget should work for more than one battery kind. LiFePO4 is not the
only chemistry a JK-BMS runs, so the default voltage levels of 0010 (LiFePO4
per cell, §3.5) are wrong for other packs. The owner wants:

- **A battery type** in the app settings, used to pick the default preset of
  voltages per cell. The type is "only for understanding the default
  preset": it changes defaults, nothing else.
- **A cell count** setting, next to the capacity.
- The preset should set the default **voltage** levels and, probably, the
  default **current** levels too, because each chemistry has its own charge
  and discharge rates.

## 2. Today (after 0010)

- Voltage defaults: LiFePO4 per-cell edges (3.00 / 3.20 / 3.45 / 3.55 /
  3.65 V) multiplied by the cell count of the reading (total voltage ÷
  average cell voltage, rounded). No count → one plain level.
- Current defaults: C-rate edges 0.2 / 0.5 / 0.8 C (both directions, from
  the EVE LiFePO4 datasheets) times the capacity (`BatteryMath.fullCapacityAh`).
- The Capacity section holds one field, the capacity in Ah.
- Own levels (Edit, 0010 §3.5) are a copy and never follow a default.

## 3. Direction (to detail)

- The Capacity section becomes a pack section: capacity (as today), cells
  and type. Since 0012 it sits on the Battery tab, so its title should not
  repeat the tab name (for example "Battery pack" / "Акумуляторна збірка";
  to decide in the detailing).
- Only the battery values change. The grid and output voltages (0012) have
  their own defaults and use `LevelBasis(null, null)`; the profile does not
  touch them.
- **Type:** the three chemistries JK-BMS knows: LiFePO4, Li-ion (NMC), LTO.
- **Cells:** blank means "work it out from the reading" (today's rule).
- **Presets per type:** voltage edges per cell (six levels like 0010's
  LiFePO4 table) and C-rate edges for charge and for discharge, from
  datasheets and common practice. LiFePO4 keeps 0010's numbers.
- Changing the type or the cells changes only the defaults; own levels stay
  as they are.
- Stored fields default to `null` (0001 §14): no type, no cell count.
- From 0011 §7: default current and voltage lists reuse level ids when the
  capacity or the cell count becomes known; re-check the alert state
  (`CacheState.levelAlerts`) when this spec changes `LevelBasis`.

## 4. Open questions

1. With no type set: keep today's LiFePO4 defaults (0010), or show the
   voltage plain until the owner picks a type?
2. The presets for Li-ion (NMC) and LTO: voltages per cell and C-rates per
   direction (research, then the owner's OK, table by table).
3. Cells: the allowed range (JK-BMS packs have up to 24 or 32 cells) and
   what to do when the setting and the reading disagree.
4. Should the type also change other defaults (temperatures: LTO and NMC
   have other safe ranges)?
5. Later, not now: read the BMS's own limits (OVP, UVP, charge voltage)
   from Home Assistant instead of a preset.
