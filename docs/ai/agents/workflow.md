# Workflow

- Integration branch: `develop`. Each spec is built on its own branch
  (`NNNN-<name>`) and merged into `develop`.
- `references/` holds cloned expert repos (index: `references/README.md`,
  "What to study, by topic"). Check the matching row before you design widget,
  sync or test code. Read only: never build or run code there. Take the
  pattern, not the shape (no Hilt, Room or multi-module here).
- Library docs: use Context7 when it is connected; otherwise
  developer.android.com.
- Code, comments and docs are in English.
