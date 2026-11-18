# User token — design

- **Date:** 2026-11-18
- **Status:** approved by the owner (2026-11-18).
- **Set:** `0009`, the first spec of epic `epic-widget-improvements` (from
  `develop` d5b9b7c). Code on branch `0009-user-token`, from the epic; it
  merges back into the epic after review.
- **Builds on:** spec 0001 (§8 architecture, §14 stored data), spec 0004
  (§5.3 finding the temperature sensors). On one topic the newest spec wins.

## 1. Intent

Today the app reads Home Assistant only through `POST /api/template`. HA
marks that endpoint `@require_admin`, so the app works only with a token of
an **administrator** user. The owner wants the app to also work with a token
of a **non-admin** user, so that a phone does not have to keep an admin token.

A non-admin user can read states: `GET /api/states/<entity_id>` checks read
permission per entity, and `GET /api/states` returns the entities the user
can read (HA core `components/api/__init__.py`, checked 2026-11-17).

A non-admin token is **not read-only**: it still can call services (switch
lights and so on). It cannot change config, users or integrations, or render
templates. The app only reads, whichever token it gets.

## 2. Goals, non-goals, anti-goals

**Goals**

- A "Token type" choice: **Admin** (today's path, one template request per
  sync) or **User** (one `GET /api/states/<id>` per configured entity).
- Both types give the same `BatteryReading` for the same HA states.
- One saved token per type: switching the type uses that type's token, so
  the user never types it again. A "Remove token" button deletes the
  selected type's token (the way to drop the admin token from the phone).
- Sensor search (Fill, spec 0004) works with both types.
- Works through KeenDNS (direct and cloud mode) like today: same host, same
  TLS, same `Authorization` header.

**Non-goals**

- Sync faster than 15 min (a later spec may add 5 min best effort).
- Auto-detecting the token type.
- WebSocket API, live updates.
- Any change to the entity settings or the widget's look.

**Anti-goals** (a failure even if it works)

- The admin path changes behaviour, or old saved settings stop working.
- A token reaches a log, an error, UI state, or a URL.
- A sync sends the other type's token, or Remove deletes both tokens.
- A sync with a user token opens one TLS connection per entity at the same
  time (18 handshakes through KeenDNS cloud).
- Anything is written to Home Assistant.

## 3. Owner decisions (2026-11-17/18)

1. Keep the template path for an admin token ("I like one request for all
   data per sync"); a user token reads each entity on its own.
2. A setting chooses the type; default **Admin**. Not one path for both.
3. The user path reads **per entity**, not the whole `/api/states` (data:
   about 14 KB per sync, ~1.3 MB a day at 15 min; the whole list can be
   hundreds of KB per sync).
4. The choice sits in the **Access token** section (layout A).
5. The sync interval stays 15 min in this spec.
6. (2026-11-18) The app keeps one token per type, so switching needs no
   re-typing; plus a "Remove token" button for the selected type (owner
   took Claude's pick).

## 4. Approaches (Claude's calls)

**A. Where the user path lives**

- A1: one `HomeAssistantSource` that branches on the token type inside:
  least wiring, but the working class grows and mixes two protocols.
- **A2 (chosen):** a new `StatesSource` for the user path and a small router
  `TokenTypeSource : BatterySource` that passes each call to the template
  source or the states source by `connection.tokenType`. The shared HTTP part
  moves out of `HomeAssistantSource` into one helper both use (refactor, no
  behaviour change). The template path keeps its logic and tests; the new
  path is tested on its own; the port gets its second real implementation
  (architecture rule: an interface only with a second implementation).

**B. User sync request**

- B1: one `GET /api/states`, keep our entities: one request, but the whole
  HA in every sync.
- **B2 (chosen, owner):** `GET /api/states/<id>` per entity, at most 4 at a
  time, over OkHttp's connection pool.

**C. How the type is chosen**

- **C1 (chosen, owner):** an explicit setting.
- C2: try the template, fall back to states on 401: hides what the app does,
  and a wrong token then fails twice.
- C3: states for every token: one path, but changes the working admin path.

## 5. Design

### 5.1 Settings (`domain/model/Settings.kt`)

- `enum class TokenType { ADMIN, USER }`.
- `ConnectionSettings(url: String = "", tokenType: TokenType = TokenType.ADMIN)`.
  Default per 0001 §14's exception (like `ThemeChoice`, spec 0008): not user
  data, and old stored settings load as ADMIN, so nothing changes for them.
- `MainViewModel.saveConnection(url)` must keep the stored type: build the
  new value with `copy(url = …)` from the stored connection, not
  `ConnectionSettings(url)` (that would reset the type to ADMIN).
- Stored JSON: `StoreJson` encodes defaults, so new writes carry
  `"tokenType"`; `schemaVersion` stays 1 (a new field with a default, like
  `appearance` in 0008). `StoredJsonV1Test` (old JSON without the field)
  must stay green.

### 5.2 Tokens, one per type (`domain/port`, `data/secure`, `data/store`)

- `TokenRecord(schemaVersion = 1, encrypted: String? = null,
  userEncrypted: String? = null)`. `encrypted` stays the **Admin** slot: a
  token saved before this spec was used with the template, so it is an
  admin token and needs no migration. `schemaVersion` stays 1.
- `TokenStore` takes the type:
  `savedTypes: Flow<Set<TokenType>>`, `get(type)`, `set(type, token)`,
  `clear(type)`. A slot that cannot be decrypted is cleared alone (today's
  rule, per slot). Both slots use the same Keystore key.
- Readers use the **active** type (`settings.connection.tokenType`):
  - `SyncBattery`: `tokens.get(type)`; an empty slot → `NotConfigured`, as
    today with no token.
  - "has token" for `BatteryOverview` / `Validation.isConfigured` (app and
    widget, `BatteryWidget`): `type in savedTypes`. An empty active slot
    shows "Set up in the app", as today.
  - Sensor search (`MainViewModel`): the active type's token.
- `MainViewModel.saveToken` writes the active type's slot;
  `removeToken()` clears the active type's slot only, then redraws widgets
  (no sync).

### 5.3 Access token section (`ui/sections/TokenSection.kt`)

```
┌ Access token            Saved · User ┐
│ Token type   [  Admin  |  User  ]    │
│ [•••••••••••••••••••••]           👁 │
│ Token saved                          │
│ [Save]   [Remove token]              │
│ ⚠ hint (only in the case below)      │
└──────────────────────────────────────┘
```

- Segmented buttons Admin / User at the top of the section, the same
  control as the preview size tabs (`SingleChoiceSegmentedButtonRow`): the
  field below belongs to the selected type.
- A tap saves the type at once, without Save (like the theme, spec 0008),
  and syncs (`afterSave(syncAfter = true)`): the type changes how data is
  read and which token is sent. A typed but unsaved draft is cleared on a
  type change, so it cannot land in the other slot.
- The field's hint "Token saved" and the summary follow the **selected**
  type: "Saved · Admin", "Not set · User" and so on.
- Save stores the typed token in the selected type's slot.
- "Remove token" shows only when the selected slot has a token. It asks
  first ("Remove the User token from this phone?", Remove / Cancel), then
  clears only that slot.
- **Hint:** when the last sync error is `Unauthorized` and the type is
  ADMIN, the section shows one line under the control: "A token of a user
  who is not an administrator needs token type User." Nothing in USER mode:
  there a 401 means the token itself is wrong. It reads `overview.error`
  (already in `MainUiState`). Right after a tap the old error stays until
  the new sync ends (under a second as a rule); that short flicker is
  accepted.
- A type change does not clear the cache: as after any failed sync, the
  widget keeps the last good reading and shows the error icon (0001 §13).
  `SyncBattery` does not change.

### 5.4 Sources (`data/ha/`)

- **`HaHttp`** (name is the builder's): the code that `HomeAssistantSource`
  has today for one request: `UrlPolicy.check`, the base URL + path,
  `Authorization: Bearer`, the header-value guard (→ `Unauthorized`), no
  redirects, 401/403 → `Unauthorized`, other non-2xx → `HttpError(code)`,
  `IOException` → `Unreachable`, body as text. It gets a GET as well as the
  current POST. `HomeAssistantSource` uses it; its behaviour and tests do
  not change.
- **`TokenTypeSource`**: `BatterySource`; `fetch` and
  `listTemperatureSensors` go to `HomeAssistantSource` for ADMIN, to
  `StatesSource` for USER. `AppContainer` wires it as the one `BatterySource`.
- **`StatesSource.fetch`:**
  1. Collect the ids: the 10 single fields and the temperature list, skip
     blank ones, drop duplicates (one request per id). An id that does not
     match the entity-id pattern of `Validation` gives no value and no
     request (the template path also yields none for it).
  2. Request `GET {url}/api/states/{id}` for each, at most **4 at a time**
     (a coroutine `Semaphore`; blocking `execute()` calls do not obey
     OkHttp's per-host limit). The id goes in as one encoded path segment.
  3. Each answer: 200 → the `state` text; 404 → no value (missing entity,
     like `states('x') | float(none)` today); any other failure fails the
     whole sync with that `SyncError`, and the calls still running are
     cancelled (`coroutineScope` + `async`). When several fail, the first
     failure to arrive wins; tests use one failing entity. The 10 s call
     timeout applies to each request, not to the whole sync.
  4. Numbers: `state.toDoubleOrNull()`; `unavailable`, `unknown`, empty and
     non-finite → no value. Balancing: the raw text through the existing
     `parseBalancing`.
  5. Build the reading through the same mapping as the template path
     (`TemplateResponse.toReading`, or a shared function the builder pulls
     out of it), so both paths have the same null rules: no charge →
     `BadResponse`.
- **`StatesSource.listTemperatureSensors`:** the same prefix check as today
  (no request for an invalid prefix); one `GET /api/states` only when the
  user taps Fill; keep each item's `entity_id`, then the same sort, filter
  and limit of 8 as `TemperatureSensors.parse` (shared code, not a copy).
  This one call can be large: it gets a longer call timeout (30 s) than the
  sync calls (10 s), through `client.newBuilder().callTimeout(…)`, which
  keeps the shared connection pool. Bad JSON → `BadResponse`.
- The entity-id check reuses `Validation`'s pattern as it is; it allows
  upper case, which HA answers with 404, so the value is missing, as on the
  template path.

### 5.5 Strings, tools, docs

- New texts in `values/` and `values-uk/` in the same commit: "Token type",
  "Admin", "User", the summary forms, the hint, "Remove token" and its
  dialog.
- `tools/mock_ha.py`: `GET /api/states/<id>` answers for the ids Fill makes
  from `--prefix` (`EntityFill`), with the same option values; other ids →
  404. `GET /api/states` lists them. New flag `--user-token`: then
  `POST /api/template` answers 401 (HA's `@require_admin`). `--status`
  works for every endpoint and wins over `--user-token`.
- README: the token type, how to make a non-admin token (HA → People →
  add a person without "Administrator" → log in as that user → Profile →
  Security → Long-lived access tokens), and that it is not read-only.
- `docs/ai/agents/`: AGENTS.md hub line (it names only `/api/template`),
  `architecture.md` (`data/ha/` sources, router; tokens per type) and
  `security.md` (two token slots, Remove).

## 6. Tests

- **Settings:** old stored JSON without `tokenType` loads as ADMIN;
  `saveConnection` keeps a USER type.
- **Tokens:** an old `TokenRecord` loads as the Admin slot; set / get /
  clear per slot leave the other slot alone; an unreadable slot is cleared
  alone; `savedTypes` follows both slots.
- **Readers:** `SyncBattery` sends the active type's token (fake store with
  two different tokens); an empty active slot → `NotConfigured` and "Set up
  in the app" while the other slot has a token.
- **`HaHttp` / `HomeAssistantSource`:** the existing tests pass unchanged.
- **`StatesSource`** (MockWebServer): all values → the same reading as the
  template path for the same states; blank fields make no request;
  duplicates make one request; 404 → no value; `unavailable` / `unknown` /
  `"abc"` → no value; no charge → `BadResponse`; one 401 → `Unauthorized`;
  503 → `HttpError(503)`; server down → `Unreachable`; never more than 4
  requests open at once; the path is encoded; the token is in the header
  and nowhere in the URL.
- **Sensor search:** list with 1, 2, 10 and foreign ids → sorted, filtered,
  cut at 8; bad JSON → `BadResponse`; invalid prefix → no request.
- **`TokenTypeSource`:** ADMIN and USER reach the right source.
- **ViewModel / UI:** a type tap saves and syncs and drops the draft; Save
  writes the selected slot; Remove (after the dialog) clears only the
  selected slot and shows only when it has a token; the summary per type;
  the hint shows only for `Unauthorized` + ADMIN.
- **Manual:** `tools/mock_ha.py --user-token …`: ADMIN shows "Token rejected"
  and the hint; USER syncs and Fill finds the sensors; switch back and
  forth with no re-typing; Remove the admin token. Owner on the phone: a
  real non-admin token, through KeenDNS.
- **Done gate:** `./gradlew lint testDebugUnitTest assembleRelease
  :app:compileDebugAndroidTestKotlin`. Instrumented tests only if the owner
  asks for them.

## 7. Left to the builder

Class and helper names; whether the reading mapping is shared through
`TemplateResponse` or a new function; the Ukrainian wording; the exact hint
placement inside the section.
