# Workflow

- Integration branch: `develop`. Each spec is built on its own branch
  (`NNNN-<name>`) and merged into `develop`.
- A set of related specs goes on an epic branch (`epic-<name>`, e.g.
  `epic-widget-v5` for 0004–0008): each spec branches from the epic and merges
  back after its review; the epic merges into `develop` after the owner's review.
- A small change without a spec gets a short kebab-case branch
  (`fix-widget-shape`, `lint-cleanup`). A one-commit change may go straight on
  `develop`.
- Merge with a merge commit (`git merge --no-ff`).
- After the merge is pushed, delete the branch, local and on origin. The
  repository keeps only `main` and `develop`.
- Ideas for later live in `docs/ideas.md`; an idea is not a promise. Add a row
  when a spec leaves something for later or the owner floats an idea. When the
  owner picks one, brainstorm it into a spec and remove its row.
- `references/` holds cloned expert repos (index: `references/README.md`,
  "What to study, by topic"). Check the matching row before you design widget,
  sync or test code. Read only: never build or run code there. Take the
  pattern, not the shape (no Hilt, Room or multi-module here).
- Library docs: use Context7 when it is connected; otherwise
  developer.android.com.
- Code, comments and docs are in English.
- License: Apache-2.0 (`LICENSE`). Add only code or assets whose license allows
  it (Apache-2.0, MIT, BSD); never copy GPL code, also not from `references/`.

## Commits

- Subject: `type(scope): Capitalized imperative summary`, e.g.
  `feat(widget): Round strip pills 16 dp and cards 20 dp`. Summary at most 50
  characters, whole subject at most 72, no full stop at the end.
- Types: `feat`, `fix`, `refactor` (no behaviour change), `test`, `docs`,
  `build`, `chore`, `ai` (agent docs and config).
- Scope is optional: one lowercase area (`widget`, `ui`, `domain`, `data`).
  Leave it out for a change across areas.
- Body only when the subject does not say enough: the why, as `-` bullets,
  lines at most 72 characters.
- One topic per commit.
- A merge commit keeps git's own subject
  (`Merge branch '<branch>' into <target>`): no type or scope.
- AI-assisted commit: exactly one trailer,
  `Co-Authored-By: <model> <noreply@anthropic.com>`. No session link.

## Release

- `main` holds releases only: it gets merges from `develop`, never a direct
  commit.
- The app version is `versionName` / `versionCode` in `app/build.gradle.kts`.
- Steps, in order:
  1. On `develop`, one `build: Bump version to X.Y.Z` commit: the new
     `versionName` and `versionCode` + 1.
  2. Run the done gate (`commands.md`).
  3. Merge `develop` into `main`.
  4. Put the annotated tag `vX.Y.Z` on that merge commit.
  5. Push `main`, `develop` and the tag.
