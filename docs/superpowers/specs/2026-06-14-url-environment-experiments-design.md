# Design: URL/Environment-based Experiments

**Date:** 2026-06-14
**Plugin:** AB Tests (`com.brighterly.experiments`)
**Status:** Approved (design) — pending spec review

## 1. Problem & Goals

Today the plugin loads A/B experiment definitions only from **local config files**
(`config/experiments.php` / `config/experiments.json`), configured as a list of file
paths (or auto-detected). Experiment configs now live remotely, served per environment as
JSON, e.g.:

```
https://storage.googleapis.com/brighterly-dev-configs/configs.json
```

We want to:

1. Load experiments from a **remote URL** instead of (or in addition to) local files.
2. Support **multiple labeled environments** (development, staging, sandbox, production,
   local), each with its own URL, and let the user pick the **active environment** from the
   **status bar** (bottom IDE menu).
3. **Sync** — manually refetch the active environment's JSON, plus auto-fetch on startup
   and on environment switch.
4. **Deprecate** the file-based source, hidden behind an off-by-default settings checkbox.

All existing IDE features (Ctrl+click navigation, Find Usages, hover docs, inlay hints,
strikethrough, branch-sum warnings, status bar count) must keep working against the active
environment.

## 2. Remote JSON format (confirmed)

The remote `configs.json` differs from the legacy local-file format — experiments are
**nested under an `"experiments"` key**, alongside an unrelated `"settings"` key we ignore:

```json
{
  "settings": { "...": "ignored" },
  "experiments": {
    "exp-23_pad-change-wording": {
      "branches": [
        { "value": "original", "percentage": 80 },
        { "value": "test", "percentage": 20 }
      ],
      "override_branch": null,
      "start_date": null
    }
  }
}
```

Legacy local files put `exp-*` keys at the **top level** (no `experiments` wrapper). The
parser must support **both** shapes.

## 3. Data model & settings

`ExperimentsSettings.State` (app-level `PersistentStateComponent`, storage
`experimentsPlugin.xml`) gains:

```kotlin
data class State(
    // NEW — environment model
    var environments: MutableList<Environment> = defaultEnvironments(),
    var activeEnvironment: String = "development",
    var showFileConfigs: Boolean = false,   // OFF = "don't show experiments from files" (default)

    // DEPRECATED — kept for migration & when showFileConfigs == true
    var configFilePath: String = "",        // already-legacy single path
    var configs: MutableList<ConfigEntry> = mutableListOf(),
)

data class Environment(var label: String = "", var url: String = "")
```

`defaultEnvironments()` seeds 5 editable rows (the list is editable — add/rename/remove):

| label         | default url (editable; `development` confirmed, rest are guessed `brighterly-<env>-configs` pattern) |
|---------------|------------------------------------------------------------------------------------------------------|
| `development` | `https://storage.googleapis.com/brighterly-dev-configs/configs.json`        (confirmed)               |
| `staging`     | `https://storage.googleapis.com/brighterly-staging-configs/configs.json`    (guess)                  |
| `sandbox`     | `https://storage.googleapis.com/brighterly-sandbox-configs/configs.json`    (guess)                  |
| `production`  | `https://storage.googleapis.com/brighterly-prod-configs/configs.json`       (guess)                  |
| `local`       | `https://storage.googleapis.com/brighterly-local-configs/configs.json`      (guess)                  |

The user corrects any wrong URLs in **Settings → Tools → AB Tests**.

### Migration

`loadState()` already migrates the legacy single `configFilePath` into `configs`. We extend
it so that a state with no `environments` (older plugin versions) is seeded with the 5
defaults, and `activeEnvironment` defaults to `development`. Existing users keep their
`configs`, but they are only consulted when `showFileConfigs == true`.

## 4. Components

### 4.1 `RemoteConfigFetcher` (new)

- Single responsibility: given an `Environment`, fetch its URL and return the raw JSON text.
- Uses IntelliJ's `com.intellij.util.io.HttpRequests` on a **background thread** (never the
  EDT). Reasonable connect/read timeouts.
- Returns a small result type: `Success(rawJson)` or `Failure(message, throwable?)`.
- No parsing here — just transport. Keeps transport testable/mockable separately from
  parsing.

### 4.2 Cache files

- On a successful fetch, the raw JSON is written verbatim to a per-environment cache file:
  `PathManager.getSystemDir()/brighterly-experiments/<sanitized-label>.json`.
- This **local file** is what powers Ctrl+click / Find Usages / "open config" — navigation
  resolves against a real `VirtualFile`/PSI, so no special-casing is needed for the absence
  of a local file.
- A failed fetch **never** overwrites or deletes a good cache file (offline resilience).

### 4.3 `ExperimentsService` (modified)

- `resolvedConfigs()` returns sources in priority order:
  1. The **active environment's cache file** as a `ConfigEntry(cachePath, JSON)` (if it
     exists).
  2. **Only if `showFileConfigs == true`:** the deprecated file sources
     (`configs` list, or auto-detected `config/experiments.{php,json}`).
- `getAll()` merges all sources; on key conflict the **earlier (URL) source wins**
  (`putIfAbsent` semantics rather than `putAll` for lower-priority sources).
- New `syncActiveEnvironment()`:
  1. Resolve active `Environment`.
  2. Background fetch via `RemoteConfigFetcher`.
  3. On success: write cache file, `invalidateCache()`, refresh widget + `DaemonCodeAnalyzer`.
  4. On failure: log, show a balloon notification, retain old cache, set widget warning state.
- Tracks a lightweight sync status (`Idle` / `Syncing` / `Error(message)`) for the widget.
- The existing VFS listener stays (so editing the cached file or a still-enabled local file
  re-indexes).
- Startup sync: a `ProjectActivity` (or app service init) triggers `syncActiveEnvironment()`
  once if the active env's cache is missing/empty.

### 4.4 Status bar widget (modified + modernized)

- Migrate from deprecated `StatusBarWidget.TextPresentation` to `CustomStatusBarWidget`
  (a `JBLabel` in a `Wrapper`, mouse listener for clicks) — matching the modernization
  already shipped in the sibling tests-links plugin.
- **Text:** `Exp: <activeEnv> ✓ <count>` normally; `Exp: <activeEnv> ⏳` while syncing;
  `Exp: <activeEnv> ⚠` when empty/errored.
- **Click popup:**
  - List of environments with a ✓ on the active one — selecting one sets it active and
    triggers a background sync.
  - `↻ Sync now` — refetch the active environment.
  - If `showFileConfigs == true`: a separated, labeled **deprecated** section listing the
    per-file breakdown + open-file actions (current behavior).
  - `Settings…` — opens the AB Tests settings page.

### 4.5 `JsonExperimentsParser` (modified)

- Wrapper-aware: if root has an `"experiments"` JSON object, iterate **its** entries;
  otherwise iterate top-level entries (legacy). Same `exp-` prefix filter and branch parsing.
- Start reading `start_date` into `ExperimentData.startDate` (currently always null).

### 4.6 `ExperimentKeyReference.findInJsonFile()` (modified)

- When resolving a key in a JSON config file, look for the property both at the top level
  **and** inside an `"experiments"` object, so navigation lands on the right key in cached
  remote files.
- `ExperimentConfigGotoUsagesHandler`, `ExperimentReferencesSearcher`, and
  `ExperimentConfigInlayHintsProvider` already operate on nested JSON keys correctly
  (verified) — the nested property's key element still has a `JsonProperty` parent and the
  `exp-` prefix filter still applies. No change needed there beyond the central parser fix.

### 4.7 Settings UI (`ExperimentsSettingsConfigurable`, modified)

- New top section: **Environments** — an editable table/rows of `label` + `url`, plus a
  combo/selector for the **active environment** (kept in sync with the status bar).
- Checkbox: **"Show experiments from local files (deprecated)"** (default OFF).
- Existing config-file rows move under a labeled **"Legacy file configs (deprecated)"**
  section, enabled/visible only as legacy. `apply()` persists environments + active env +
  checkbox + (legacy) configs, then `invalidateCache()` and triggers a sync.

## 5. Data flow

```
Startup / env switch / "Sync now"
        │
        ▼
ExperimentsService.syncActiveEnvironment()
        │  (background thread)
        ▼
RemoteConfigFetcher.fetch(activeEnv.url)
        │  Success(rawJson)                 Failure
        ▼                                     │
write cache file <env>.json                   ▼  keep old cache,
        │                              balloon + widget ⚠
        ▼
invalidateCache()  ──► WindowManager.updateWidget(...)
        │             DaemonCodeAnalyzer.restart() (open projects)
        ▼
getAll(): parse active cache file (+ legacy files if enabled)
        │
        ├─► inlay hints / strikethrough / hover docs   (key lookup only)
        └─► Ctrl+click / Find Usages                   (resolve against cache file PSI)
```

## 6. Error handling

- All network I/O on background threads; all UI mutation via `invokeLater`.
- Fetch failure (timeout, non-200, network down): logged, balloon notification, last good
  cache retained, widget shows `⚠`. The plugin remains usable with stale data.
- Malformed JSON: parser returns `emptyMap()` (already `runCatching`-guarded); the bad
  payload is still cached so the user can inspect it, but `getAll()` yields no experiments
  and the widget shows `⚠`.
- Missing/blank URL for the active environment: skip fetch, widget `⚠`, hint to open
  Settings.

## 7. Testing

- **Parser:** extend `JsonExperimentsConfigParserTest` — wrapped `{"experiments": {...}}`
  format, top-level legacy format, `start_date` parsing, and `settings`-key-only input
  yielding no experiments.
- **Fetcher/cache:** unit test parsing a checked-in fixture of the real `configs.json`;
  verify cache-file write path and that a failed fetch leaves an existing cache intact
  (transport mocked).
- **Settings migration:** legacy `configs` present + no `environments` → 5 defaults seeded,
  `activeEnvironment == "development"`, legacy configs preserved but inert while
  `showFileConfigs == false`.
- **Merge precedence:** with `showFileConfigs == true` and a key present in both the active
  env and a local file, the URL value wins.

## 8. Out of scope (YAGNI)

- Background polling / auto-refresh on a timer (only startup + switch + manual).
- Authenticated/private URLs (the buckets are public).
- Editing experiments through the IDE / writing back to the remote.
- Parsing the remote `"settings"` block (ignored entirely).
- Per-project (vs. app-level) environment selection — selection stays app-level, matching
  today's settings scope.

## 9. Touch-point summary (files)

| File | Change |
|------|--------|
| `model/Environment.kt` | **new** — `Environment(label, url)` |
| `settings/ExperimentsSettings.kt` | add `environments`, `activeEnvironment`, `showFileConfigs`; seed + migrate |
| `settings/ExperimentsSettingsConfigurable.kt` | environments table + active selector + deprecation checkbox; legacy section |
| `service/RemoteConfigFetcher.kt` | **new** — background HTTP transport |
| `service/ExperimentsService.kt` | cache files, `syncActiveEnvironment()`, sync status, source precedence |
| `statusbar/ExperimentsStatusBarWidgetFactory.kt` | `CustomStatusBarWidget`; env switcher + sync popup |
| `parser/JsonExperimentsParser.kt` | wrapper-aware; `start_date` |
| `reference/ExperimentKeyReference.kt` | resolve nested `experiments` keys |
| `startup/ExperimentsStartupActivity.kt` | **new** — initial sync on project open |
| `plugin.xml` | register startup activity; description update |
| `README.md` | document environments as primary, files as legacy |
| tests | parser + fetcher + migration + merge precedence |
