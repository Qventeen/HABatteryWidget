# Workflow

- Integration branch: `develop`. Each spec is built on its own branch
  (`NNNN-<name>`) and merged into `develop`.
- A set of related specs goes on an epic branch (`epic-<name>`, e.g.
  `epic-widget-v5` for 0004–0008): each spec branches from the epic and merges
  back after its review; the epic merges into `develop` after the owner's review.
- The app version is `versionName` / `versionCode` in `app/build.gradle.kts`.
- `references/` holds cloned expert repos (index: `references/README.md`,
  "What to study, by topic"). Check the matching row before you design widget,
  sync or test code. Read only: never build or run code there. Take the
  pattern, not the shape (no Hilt, Room or multi-module here).
- Library docs: use Context7 when it is connected; otherwise
  developer.android.com.
- Code, comments and docs are in English.
