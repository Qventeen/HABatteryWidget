# References

Read-only example code for the clean rewrite (spec `0001`). Use it to check
how experts solve a problem before writing our own code. The cloned folders
are ignored by git; only this index is tracked.

## Rules

1. **Read, don't run.** Never build, run or execute anything from these repos.
   They are third-party code.
2. **Learn the pattern, don't copy the shape.** Most repos use Hilt, Room and
   many modules. Our spec uses one module and manual DI (KISS). Take the idea
   and write it in our style.
3. **Copying code:** all 7 repos are Apache-2.0. If a whole file or a large
   block is copied, keep the license header and note the source.
4. **Pinned versions.** The table shows the commit each clone was taken from.
   Re-check against current docs before relying on a version number.

## The set (7 repos, cloned 2026-11-14)

| Folder | Source | Commit (date) | Why it is here |
|---|---|---|---|
| `platform-samples` | [android/platform-samples](https://github.com/android/platform-samples) — sparse: `samples/user-interface/appwidgets` | `1ffa0db12320` (2026-11-03) | Official Glance code for the **canonical widget layouts**; the docs link here as the reference. |
| `androidx-glance` | [androidx/androidx](https://github.com/androidx/androidx) — sparse: `glance/glance`, `glance-appwidget`, `glance-appwidget-testing`, `glance-testing` | `6b370d7ad9fd` (2026-11-14) | Glance **source of truth**: demos, samples and the library's own tests. |
| `android-skills` | [android/skills](https://github.com/android/skills) | `5815d27fda56` (2026-11-09) | Google's agent skills. Two are about Glance: updates/memory and canonical layouts. |
| `socialite` | [android/socialite](https://github.com/android/socialite) | `d7385bbe08ea` (2026-10-08) | Official sample app with a **real Glance widget**: config activity, generated previews, repository. Used by the Glance codelab. |
| `ha-android` | [home-assistant/android](https://github.com/home-assistant/android) — sparse: `widgets/`, widget tests, `util/compose`, `common/data` | `6f4833791015` (2026-11-13) | **Same domain**: how the official HA app talks to HA and builds widgets. |
| `architecture-samples` | [android/architecture-samples](https://github.com/android/architecture-samples) | `ee66e1526b84` (2025-07-17, `main`) | **Architecture at our size**: one app module, ~55 Kotlin files, layers, fakes in tests. |
| `nowinandroid` | [android/nowinandroid](https://github.com/android/nowinandroid) — sparse: `sync/work`, `core/{datastore,data,testing,model,common}`, `docs` | `2982e63cb691` (2026-10-20) | Best reference for **WorkManager sync**, DataStore and test doubles. |

## What to study, by topic

| Topic in our spec | Look here |
|---|---|
| Widget layout, sizes, colours | `platform-samples/samples/user-interface/appwidgets/.../glance/layout/` · `androidx-glance/.../demos/ExactAppWidget.kt`, `ResponsiveAppWidget.kt` |
| Progress bar (replaces our bitmap) | `androidx-glance/.../demos/ProgressIndicatorAppWidget.kt` |
| Error UI instead of a crash | `androidx-glance/.../demos/ErrorUiAppWidget.kt` |
| Widget state and update after a sync | `android-skills/system/glance-appwidget-updates-and-memory/` (`SKILL.md` + `references/WidgetStateAndActions.kt`) |
| Widget config activity, previews | `socialite/app/src/main/java/.../socialite/widget/` |
| Testing Glance UI | `androidx-glance/glance/glance-appwidget-testing/`, `glance-testing/` · `ha-android/app/src/test/.../widgets/` |
| HA API and widgets in the HA app | `ha-android/.../widgets/template/` (classic RemoteViews) · `widgets/todo/TodoGlanceAppWidget.kt` (Glance) |
| Layers, ViewModel, fakes | `architecture-samples/app/` and `shared-test/` |
| Periodic sync with WorkManager | `nowinandroid/sync/work/.../workers/SyncWorker.kt`, `status/WorkManagerSyncManager.kt` |
| DataStore | `nowinandroid/core/datastore/` |

**Not to copy:** Hilt/Room/multi-module setup (`nowinandroid`, `ha-android`,
`socialite`); Coil image loading in `platform-samples`; RemoteViews code in
`ha-android/.../template/` (we use Glance).

## Docs (not cloned)

- [Glance: create an app widget](https://developer.android.com/develop/ui/compose/glance/create-app-widget)
- [Glance samples and showcases](https://developer.android.com/develop/ui/compose/glance/additional-resources)
- [Canonical widget layouts](https://developer.android.com/design/ui/mobile/guides/widgets/layouts)
- [Widget quality tiers](https://developer.android.com/design/ui/mobile/guides/widgets/widget_quality_guide)
- [Case study: Gratitude moved widgets to Glance (2026)](https://android-developers.googleblog.com/2026/05/how-gratitude-widgets-boosted-user-retention-25-percent.html) — keep receiver class name and package stable, or placed widgets disappear.
- [Android skills philosophy (2026)](https://android-developers.googleblog.com/2026/08/android-skills-philosophy.html) — install only the skills you need.

## Candidates dropped and why

- `google/glance-experimental-tools` — archived in 2025; its ideas moved into Glance.
- `Seo-4d696b75/glance-widget-demo`, `chrimaeon/favorite-apps-widget`,
  `pmjsankar/Battery-Widget-Jetpack-Glance` — small (1–11 stars), not
  maintained since 2024–2025, no expert review.
- `android/snippets` — repeats what the docs and the androidx samples show.
- `android/compose-samples` — Compose screens, little about widgets.

## Refresh a clone

```bash
git -C references/<folder> pull --depth 1
```

To clone the set again, use `git clone --depth 1 --filter=blob:none --sparse <url>`
plus `git sparse-checkout set <paths>` with the paths from the table.
