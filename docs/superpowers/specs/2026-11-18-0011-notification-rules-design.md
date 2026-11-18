# Notification rules — concept

- **Date:** 2026-11-18
- **Status:** concept. Not approved. It needs a detailing session before a
  plan is written.
- **Set:** `0011`, epic `epic-widget-improvements`. Code later on branch
  `0011-notification-rules`, from the epic.
- **Builds on:** spec 0001 (§6 full-charge notification, §13 refresh, §14
  stored data). May build on 0010 (see question 1). On one topic the newest
  spec wins.

## 1. Intent (owner, 2026-11-18)

Today the app sends a notification only when the battery is full. The owner
wants a settings sub-menu where each notification can be turned on and off,
and where the owner can add new ones. The first kind is battery charge
levels: a notification when the charge reaches a level the owner sets. For
each notification the owner picks its type: **Error** (danger, red),
**Warning** (yellow), **Success** (green) or **Full** (full charge, blue).

## 2. Today

- `FullChargeRule` fires when the battery state is FULL and re-arms (gets
  ready to fire again) when the rounded charge drops to ≤ 98 %. The "already
  notified" flag is `CacheState.fullChargeNotified`.
- `AndroidFullChargeNotifier` posts to one channel (`full_charge`, high
  importance) with one notification id; a tap opens the app. It never asks
  for the permission; `NotificationSection` asks on Android 13+.
- One setting: `Settings.notifyOnFull`.
- Rules are checked only inside `SyncBattery`, that is, only when a sync
  runs. The periodic sync runs when auto refresh is on and (a widget exists
  or the full-charge notification is on) (0001 §13).

## 3. Proposed shape (a proposal, not decided)

- **A rule list.** A rule has: a value (SOC first), a condition (falls to
  ≤ X or rises to ≥ X), a type (Error, Warning, Success, Full), on/off, and
  maybe its own title.
- **Fire once per crossing.** A rule re-arms only after the value moves back
  past X by a margin (hysteresis: a small gap so a value near the line does
  not fire again and again; today it is 2 % for full). The per-rule "already
  notified" state lives in the cache and replaces `fullChargeNotified`.
- **One Android channel per type** (4 channels), so the owner can set sound,
  vibration and pop-up per type in the Android settings. The type also sets
  the icon, the accent colour and the default title.
- **Migration:** the current "notify on full" switch becomes the first rule
  (Full, SOC 100 %, same on/off). This is a meaning change after a release,
  so it bumps `schemaVersion` and adds a `DataMigration` with a test (0001
  §14).
- **Sync condition:** the periodic sync also runs when any rule is on
  (replaces "the full-charge notification is on").
- **Domain:** a pure evaluator takes the reading, the rules and the stored
  state, and returns what to post and the new state; a notifier port posts
  it.

## 4. Catches

- **Delay.** Rules are checked only during a sync: every 15 min at best, and
  only with auto refresh on. Android's battery saving (Doze) can add more.
  A notification can come 15–30 min late. With auto refresh off, no
  notification comes at all; the screen should say so.
- **Colour.** Android does not colour the whole notification. The type shows
  as icon, accent colour (if the system shows it; HyperOS may not) and its
  channel's sound.
- **Channels.** After a channel is created, the app cannot change its sound
  or importance; the owner changes them in the Android settings.
- **Faster path.** For urgent alarms, a Home Assistant automation that sends
  through the HA Companion app arrives in seconds. This spec does not
  replace it.
- **Noise.** Two rules close together, or a value that moves around a line,
  can spam; the re-arm margin limits this.

## 5. Open questions (for the detailing session)

1. **Shared levels with 0010, or a separate rule list?** Shared: each colour
   band of a value can also notify when the value enters it, so widget
   colour and notification always agree. Separate: simpler, but they can
   disagree. Recommendation: shared. Example:

   | SOC | Colour | Notify |
   |---|---|---|
   | 0–15 % | red | Error |
   | 15–25 % | amber | Warning |
   | 25–99 % | green | Success ("OK again") |
   | 100 % | blue | Full |

2. Which values besides SOC (cell delta, temperatures, the inverter mode from
   0012, for example a grid loss)?
3. Own text per rule, or a fixed text per type plus the value?
4. Once per crossing, or repeat every N minutes while the value stays there?
5. Re-arm margin: fixed, or set per rule?
6. Quiet hours?
7. What does Success mean exactly (for example "charge back above 25 %")?
8. Should a long sync error (HA not reachable for N minutes) notify too?

## 6. Non-goals

- No push from a server (FCM): the app posts local notifications after its
  own sync.
- No checks more often than the 15-minute sync (no foreground service, no
  exact alarms); they would cost phone battery (proposal).
