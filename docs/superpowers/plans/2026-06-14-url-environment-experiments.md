# URL/Environment-based Experiments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load A/B experiments from per-environment remote URLs (development/staging/sandbox/production/local), let the user switch the active environment and sync from the status bar, and deprecate the file-based config source behind an off-by-default toggle.

**Architecture:** A new `RemoteConfigFetcher` does background HTTP transport; `ConfigCache` persists each environment's fetched JSON to a local file under the IDE system dir; `ExperimentsService` treats the active environment's cache file as the primary JSON source (with optional, deprecated, lower-priority local files merged in URL-wins order) and exposes `syncActiveEnvironment()`. The status bar widget (modernized to `CustomStatusBarWidget`) switches environments and triggers syncs. The JSON parser and reference resolver become aware of the remote `{"experiments": {...}}` wrapper.

**Tech Stack:** Kotlin 2.3.20, IntelliJ Platform Gradle Plugin 2.14.0 (PhpStorm 2025.3.4 / build 253), Gson (bundled), JUnit4.

**Branch:** `feat/url-environment-experiments` (already created; spec committed).

**Spec:** `docs/superpowers/specs/2026-06-14-url-environment-experiments-design.md`

**Conventions in this repo:**
- Tests are plain JUnit4 (`org.junit.Test`, `org.junit.Assert.*`) with backtick method names; run all with `./gradlew test`.
- Only pure logic is unit-tested (parsers). Platform-bound code (services, widgets, PSI, settings UI) is verified by `./gradlew compileKotlin` / `./gradlew buildPlugin` and a manual `./gradlew runIde` smoke check — match that split.
- Commit after every task.

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `model/Environment.kt` | `Environment(label, url)` value type | Create |
| `settings/ExperimentsSettings.kt` | persist environments, active env, file toggle; migrate | Modify |
| `parser/JsonExperimentsParser.kt` | parse both wrapped and flat JSON; read `start_date` | Modify |
| `service/RemoteConfigFetcher.kt` | background HTTP GET → `FetchResult` | Create |
| `service/ConfigCache.kt` | read/write per-env cache files; keep cache on failure | Create |
| `service/ExperimentsMerger.kt` | first-source-wins merge of experiment maps | Create |
| `service/ExperimentsService.kt` | sources, sync, status, change topic | Modify |
| `reference/ExperimentKeyReference.kt` | resolve nested `experiments` keys | Modify |
| `statusbar/ExperimentsStatusBarWidgetFactory.kt` | `CustomStatusBarWidget` + env/sync popup | Modify |
| `startup/ExperimentsStartupActivity.kt` | initial sync on project open | Create |
| `resources/META-INF/plugin.xml` | notification group, startup activity, description | Modify |
| `README.md`, `gradle.properties` | docs + version bump | Modify |
| test fixtures + tests | parser/cache/merger/settings | Create |

---

## Task 1: `Environment` model

**Files:**
- Create: `src/main/kotlin/com/brighterly/experiments/model/Environment.kt`

- [ ] **Step 1: Create the model**

```kotlin
package com.brighterly.experiments.model

/**
 * A named experiment source environment, e.g. label="staging",
 * url="https://storage.googleapis.com/brighterly-staging-configs/configs.json".
 *
 * `var` properties + default no-arg constructor are required by the IntelliJ
 * XML serializer used for plugin settings persistence.
 */
data class Environment(
    var label: String = "",
    var url: String = "",
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/model/Environment.kt
git commit -m "feat: add Environment model for URL-based experiment sources"
```

---

## Task 2: Settings state — environments, active env, file toggle, migration

**Files:**
- Modify: `src/main/kotlin/com/brighterly/experiments/settings/ExperimentsSettings.kt`
- Test: `src/test/kotlin/com/brighterly/experiments/settings/ExperimentsSettingsTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/brighterly/experiments/settings/ExperimentsSettingsTest.kt`:

```kotlin
package com.brighterly.experiments.settings

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import org.junit.Assert.*
import org.junit.Test

class ExperimentsSettingsTest {

    @Test
    fun `fresh state seeds five default environments`() {
        val state = ExperimentsSettings.State()
        assertEquals(5, state.environments.size)
        assertEquals(
            listOf("development", "staging", "sandbox", "production", "local"),
            state.environments.map { it.label },
        )
        assertEquals("development", state.activeEnvironment)
        assertFalse(state.showFileConfigs)
    }

    @Test
    fun `development url is the confirmed bucket`() {
        val dev = ExperimentsSettings.State().environments.first { it.label == "development" }
        assertEquals(
            "https://storage.googleapis.com/brighterly-dev-configs/configs.json",
            dev.url,
        )
    }

    @Test
    fun `loadState seeds environments when persisted state has none`() {
        val settings = ExperimentsSettings()
        val persisted = ExperimentsSettings.State().apply { environments = mutableListOf() }
        settings.loadState(persisted)
        assertEquals(5, settings.state.environments.size)
        assertEquals("development", settings.state.activeEnvironment)
    }

    @Test
    fun `loadState migrates legacy single configFilePath into configs`() {
        val settings = ExperimentsSettings()
        val persisted = ExperimentsSettings.State().apply {
            configFilePath = "/x/config/experiments.php"
            configs = mutableListOf()
        }
        settings.loadState(persisted)
        assertEquals(1, settings.state.configs.size)
        assertEquals("/x/config/experiments.php", settings.state.configs[0].path)
        assertEquals(ConfigType.PHP, settings.state.configs[0].type)
        assertEquals("", settings.state.configFilePath)
    }

    @Test
    fun `loadState preserves legacy configs but keeps file display off by default`() {
        val settings = ExperimentsSettings()
        val persisted = ExperimentsSettings.State().apply {
            configs = mutableListOf(ConfigEntry("/x/experiments.json", ConfigType.JSON))
        }
        settings.loadState(persisted)
        assertEquals(1, settings.state.configs.size)
        assertFalse(settings.state.showFileConfigs)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.brighterly.experiments.settings.ExperimentsSettingsTest"`
Expected: FAIL — compilation error (no `environments`/`activeEnvironment`/`showFileConfigs` members yet).

- [ ] **Step 3: Implement the new state + migration**

Replace the entire contents of `src/main/kotlin/com/brighterly/experiments/settings/ExperimentsSettings.kt`:

```kotlin
package com.brighterly.experiments.settings

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import com.brighterly.experiments.model.Environment
import com.intellij.openapi.components.*

@State(
    name = "ExperimentsPluginSettings",
    storages = [Storage("experimentsPlugin.xml")],
)
@Service(Service.Level.APP)
class ExperimentsSettings : PersistentStateComponent<ExperimentsSettings.State> {

    data class State(
        // Environment model (primary source going forward)
        var environments: MutableList<Environment> = defaultEnvironments(),
        var activeEnvironment: String = "development",
        // When false (default), experiments from local files are not shown.
        var showFileConfigs: Boolean = false,

        // Legacy single-path field — kept for migration only
        var configFilePath: String = "",
        // Deprecated file-based configs — only consulted when showFileConfigs == true
        var configs: MutableList<ConfigEntry> = mutableListOf(),
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state

        // Migrate legacy single-path setting to list format
        if (state.configs.isEmpty() && state.configFilePath.isNotBlank()) {
            state.configs.add(ConfigEntry(state.configFilePath, ConfigType.PHP))
            state.configFilePath = ""
        }

        // Seed environments for users upgrading from a pre-environment version
        if (state.environments.isEmpty()) {
            state.environments = defaultEnvironments()
        }
        if (state.activeEnvironment.isBlank()) {
            state.activeEnvironment = "development"
        }
    }

    companion object {
        fun getInstance(): ExperimentsSettings = service()

        /**
         * The five seeded environments. Only `development` is a confirmed URL;
         * the rest follow the `brighterly-<env>-configs` bucket pattern as an
         * editable best guess (corrected in Settings → Tools → AB Tests).
         */
        fun defaultEnvironments(): MutableList<Environment> = mutableListOf(
            Environment("development", "https://storage.googleapis.com/brighterly-dev-configs/configs.json"),
            Environment("staging", "https://storage.googleapis.com/brighterly-staging-configs/configs.json"),
            Environment("sandbox", "https://storage.googleapis.com/brighterly-sandbox-configs/configs.json"),
            Environment("production", "https://storage.googleapis.com/brighterly-prod-configs/configs.json"),
            Environment("local", "https://storage.googleapis.com/brighterly-local-configs/configs.json"),
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.brighterly.experiments.settings.ExperimentsSettingsTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/settings/ExperimentsSettings.kt \
        src/test/kotlin/com/brighterly/experiments/settings/ExperimentsSettingsTest.kt
git commit -m "feat: add environment list, active env, and file-display toggle to settings"
```

---

## Task 3: JSON parser — wrapper-aware + `start_date`

**Files:**
- Modify: `src/main/kotlin/com/brighterly/experiments/parser/JsonExperimentsParser.kt`
- Test: `src/test/kotlin/com/brighterly/experiments/parser/JsonExperimentsConfigParserTest.kt`

- [ ] **Step 1: Add failing tests**

Append these methods inside the existing `JsonExperimentsConfigParserTest` class (before the closing brace):

```kotlin
    private val wrappedJson = """
        {
            "settings": { "feature_x": true },
            "experiments": {
                "exp-23_pad-change-wording": {
                    "branches": [
                        {"value": "original", "percentage": 80},
                        {"value": "test", "percentage": 20}
                    ],
                    "override_branch": null,
                    "start_date": "2026-01-15"
                },
                "exp-88_sales-tax": {
                    "branches": [
                        {"value": "original", "percentage": 50},
                        {"value": "test", "percentage": 50}
                    ],
                    "override_branch": "test",
                    "start_date": null
                }
            }
        }
    """.trimIndent()

    @Test
    fun `parses experiments nested under experiments key`() {
        val result = JsonExperimentsParser.parse(wrappedJson)
        assertEquals(2, result.size)
        assertTrue(result.containsKey("exp-23_pad-change-wording"))
        assertEquals(80, result["exp-23_pad-change-wording"]!!.branches["original"])
        assertTrue(result["exp-88_sales-tax"]!!.isClosed)
    }

    @Test
    fun `parses start_date when present`() {
        val result = JsonExperimentsParser.parse(wrappedJson)
        assertEquals("2026-01-15", result["exp-23_pad-change-wording"]!!.startDate)
        assertNull(result["exp-88_sales-tax"]!!.startDate)
    }

    @Test
    fun `ignores settings block and non-exp keys in wrapper`() {
        val result = JsonExperimentsParser.parse(wrappedJson)
        assertFalse(result.containsKey("settings"))
        assertFalse(result.containsKey("feature_x"))
    }

    @Test
    fun `still parses flat legacy format`() {
        // sampleJson (top-level exp keys) must still work
        assertEquals(2, JsonExperimentsParser.parse(sampleJson).size)
    }

    @Test
    fun `returns empty when only settings present`() {
        val json = """{"settings": {"a": 1}}"""
        assertTrue(JsonExperimentsParser.parse(json).isEmpty())
    }
```

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `./gradlew test --tests "com.brighterly.experiments.parser.JsonExperimentsConfigParserTest"`
Expected: FAIL — `parses experiments nested under experiments key` finds 0 results (current parser only reads top-level keys); `parses start_date` fails (currently always null).

- [ ] **Step 3: Implement wrapper-awareness + start_date**

Replace the body of `parse()` in `JsonExperimentsParser.kt`:

```kotlin
package com.brighterly.experiments.parser

import com.brighterly.experiments.model.ExperimentData
import com.google.gson.JsonParser

object JsonExperimentsParser {

    fun parse(jsonContent: String): Map<String, ExperimentData> {
        val root = runCatching { JsonParser.parseString(jsonContent).asJsonObject }
            .getOrNull() ?: return emptyMap()

        // Remote/cached configs nest experiments under an "experiments" object;
        // legacy local files put exp-* keys at the top level. Support both.
        val container = runCatching { root.getAsJsonObject("experiments") }.getOrNull() ?: root

        val result = mutableMapOf<String, ExperimentData>()
        for ((key, value) in container.entrySet()) {
            if (!key.startsWith("exp-")) continue
            val obj = runCatching { value.asJsonObject }.getOrNull() ?: continue

            val branches = obj.getAsJsonArray("branches")
                ?.mapNotNull { element ->
                    val b = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                    val branchValue = b.get("value")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
                    val pct = runCatching { b.get("percentage")?.asInt }.getOrNull() ?: return@mapNotNull null
                    branchValue to pct
                }
                ?.toMap()
                ?: emptyMap()

            val overrideBranch = obj.get("override_branch")?.takeIf { !it.isJsonNull }?.asString
            val startDate = obj.get("start_date")?.takeIf { !it.isJsonNull }?.asString

            result[key] = ExperimentData(
                key = key,
                branches = branches,
                overrideBranch = overrideBranch,
                startDate = startDate,
            )
        }
        return result
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.brighterly.experiments.parser.JsonExperimentsConfigParserTest"`
Expected: PASS (all original + 5 new tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/parser/JsonExperimentsParser.kt \
        src/test/kotlin/com/brighterly/experiments/parser/JsonExperimentsConfigParserTest.kt
git commit -m "feat: parse experiments under 'experiments' wrapper and read start_date"
```

---

## Task 4: `ConfigCache` — per-environment cache files

**Files:**
- Create: `src/main/kotlin/com/brighterly/experiments/service/ConfigCache.kt`
- Test: `src/test/kotlin/com/brighterly/experiments/service/ConfigCacheTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/brighterly/experiments/service/ConfigCacheTest.kt`:

```kotlin
package com.brighterly.experiments.service

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigCacheTest {

    @JvmField @Rule val tmp = TemporaryFolder()

    private fun cache() = ConfigCache(tmp.newFolder("cache"))

    @Test
    fun `write creates a per-env json file and read returns its content`() {
        val cache = cache()
        cache.write("staging", """{"experiments":{}}""")
        assertTrue(cache.exists("staging"))
        assertEquals("""{"experiments":{}}""", cache.read("staging"))
    }

    @Test
    fun `read returns null when no cache exists`() {
        assertNull(cache().read("production"))
        assertFalse(cache().exists("production"))
    }

    @Test
    fun `cacheFile name is sanitized and lowercased`() {
        val cache = cache()
        val file = cache.cacheFile("Pro / duction")
        assertEquals("pro___duction.json", file.name)
    }

    @Test
    fun `applyFetch success writes the cache and returns the file`() {
        val cache = cache()
        val file = cache.applyFetch("dev", FetchResult.Success("""{"a":1}"""))
        assertNotNull(file)
        assertEquals("""{"a":1}""", cache.read("dev"))
    }

    @Test
    fun `applyFetch failure keeps an existing cache intact`() {
        val cache = cache()
        cache.write("dev", "GOOD")
        val file = cache.applyFetch("dev", FetchResult.Failure("offline"))
        assertNotNull(file)
        assertEquals("GOOD", cache.read("dev"))
    }

    @Test
    fun `applyFetch failure with no existing cache returns null`() {
        assertNull(cache().applyFetch("dev", FetchResult.Failure("offline")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.brighterly.experiments.service.ConfigCacheTest"`
Expected: FAIL — `ConfigCache` and `FetchResult` do not exist yet.

- [ ] **Step 3: Implement `FetchResult` + `ConfigCache`**

Create `src/main/kotlin/com/brighterly/experiments/service/ConfigCache.kt`:

```kotlin
package com.brighterly.experiments.service

import java.io.File

/** Transport result for a remote config fetch (defined here so cache tests need no network code). */
sealed interface FetchResult {
    data class Success(val rawJson: String) : FetchResult
    data class Failure(val message: String, val cause: Throwable? = null) : FetchResult
}

/**
 * Stores each environment's fetched experiment JSON as a local file so existing
 * file-based navigation (Ctrl+click / Find Usages) works against a real PSI file.
 * A failed fetch never clobbers a previously-good cache.
 */
class ConfigCache(private val baseDir: File) {

    fun cacheFile(envLabel: String): File = File(baseDir, "${sanitize(envLabel)}.json")

    fun exists(envLabel: String): Boolean = cacheFile(envLabel).isFile

    fun read(envLabel: String): String? = cacheFile(envLabel).takeIf { it.isFile }?.readText()

    fun write(envLabel: String, rawJson: String): File {
        val file = cacheFile(envLabel)
        file.parentFile?.mkdirs()
        file.writeText(rawJson)
        return file
    }

    /**
     * On success, writes and returns the cache file. On failure, returns the
     * existing cache file if present (kept intact), or null if there is none.
     */
    fun applyFetch(envLabel: String, result: FetchResult): File? = when (result) {
        is FetchResult.Success -> write(envLabel, result.rawJson)
        is FetchResult.Failure -> cacheFile(envLabel).takeIf { it.isFile }
    }

    private fun sanitize(label: String): String =
        label.lowercase().replace(Regex("[^a-z0-9._-]"), "_").ifBlank { "default" }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.brighterly.experiments.service.ConfigCacheTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/service/ConfigCache.kt \
        src/test/kotlin/com/brighterly/experiments/service/ConfigCacheTest.kt
git commit -m "feat: add ConfigCache for per-environment cached config files"
```

---

## Task 5: `ExperimentsMerger` — first-source-wins merge

**Files:**
- Create: `src/main/kotlin/com/brighterly/experiments/service/ExperimentsMerger.kt`
- Test: `src/test/kotlin/com/brighterly/experiments/service/ExperimentsMergerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/brighterly/experiments/service/ExperimentsMergerTest.kt`:

```kotlin
package com.brighterly.experiments.service

import com.brighterly.experiments.model.ExperimentData
import org.junit.Assert.*
import org.junit.Test

class ExperimentsMergerTest {

    private fun exp(key: String, pct: Int) =
        ExperimentData(key = key, branches = mapOf("original" to pct, "test" to (100 - pct)))

    @Test
    fun `earlier source wins on key conflict`() {
        val url = mapOf("exp-1" to exp("exp-1", 90))
        val file = mapOf("exp-1" to exp("exp-1", 10), "exp-2" to exp("exp-2", 50))
        val merged = ExperimentsMerger.merge(listOf(url, file))
        assertEquals(90, merged["exp-1"]!!.branches["original"]) // url wins
        assertEquals(50, merged["exp-2"]!!.branches["original"]) // file-only survives
        assertEquals(2, merged.size)
    }

    @Test
    fun `empty sources merge to empty`() {
        assertTrue(ExperimentsMerger.merge(emptyList()).isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.brighterly.experiments.service.ExperimentsMergerTest"`
Expected: FAIL — `ExperimentsMerger` does not exist.

- [ ] **Step 3: Implement**

Create `src/main/kotlin/com/brighterly/experiments/service/ExperimentsMerger.kt`:

```kotlin
package com.brighterly.experiments.service

import com.brighterly.experiments.model.ExperimentData

object ExperimentsMerger {
    /** Merges experiment maps with first-source-wins precedence on key conflicts. */
    fun merge(sources: List<Map<String, ExperimentData>>): Map<String, ExperimentData> {
        val merged = LinkedHashMap<String, ExperimentData>()
        for (source in sources) {
            for ((key, value) in source) merged.putIfAbsent(key, value)
        }
        return merged
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.brighterly.experiments.service.ExperimentsMergerTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/service/ExperimentsMerger.kt \
        src/test/kotlin/com/brighterly/experiments/service/ExperimentsMergerTest.kt
git commit -m "feat: add ExperimentsMerger with URL-wins precedence"
```

---

## Task 6: `RemoteConfigFetcher` — background HTTP transport

**Files:**
- Create: `src/main/kotlin/com/brighterly/experiments/service/RemoteConfigFetcher.kt`

> No unit test: this is thin static-platform transport (`HttpRequests`) that is not meaningfully unit-testable without networking. Its `FetchResult` consumers are tested in Tasks 4 and 5. Verify by compilation + the manual smoke check in Task 11.

- [ ] **Step 1: Implement the fetcher**

Create `src/main/kotlin/com/brighterly/experiments/service/RemoteConfigFetcher.kt`:

```kotlin
package com.brighterly.experiments.service

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import java.io.IOException

/** Fetches raw experiment-config JSON over HTTP. Transport only — no parsing. */
object RemoteConfigFetcher {

    private val logger = thisLogger()
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /** MUST be called off the EDT (network I/O). */
    fun fetch(url: String): FetchResult {
        if (url.isBlank()) return FetchResult.Failure("No URL configured")
        return try {
            val body = HttpRequests.request(url)
                .connectTimeout(CONNECT_TIMEOUT_MS)
                .readTimeout(READ_TIMEOUT_MS)
                .readString()
            FetchResult.Success(body)
        } catch (e: IOException) {
            logger.warn("Failed to fetch experiments config from $url", e)
            FetchResult.Failure(e.message ?: "Network error", e)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/service/RemoteConfigFetcher.kt
git commit -m "feat: add RemoteConfigFetcher for background HTTP config fetch"
```

---

## Task 7: `ExperimentsService` — sources, sync, status, change topic

**Files:**
- Modify: `src/main/kotlin/com/brighterly/experiments/service/ExperimentsService.kt`

> Platform-bound (app service, message bus, EDT). Verified by compile + manual smoke check (Task 11). The pure merge/cache logic it delegates to is already tested (Tasks 4–5).

- [ ] **Step 1: Replace the service implementation**

Replace the entire contents of `src/main/kotlin/com/brighterly/experiments/service/ExperimentsService.kt`:

```kotlin
package com.brighterly.experiments.service

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import com.brighterly.experiments.model.ExperimentData
import com.brighterly.experiments.parser.ExperimentsConfigParser
import com.brighterly.experiments.parser.JsonExperimentsParser
import com.brighterly.experiments.settings.ExperimentsSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Sync state of the active environment, surfaced by the status bar widget. */
sealed interface SyncStatus {
    object Idle : SyncStatus
    object Syncing : SyncStatus
    data class Error(val message: String) : SyncStatus
}

/** Fired (on the EDT) whenever the experiment index changes; the widget listens. */
fun interface ExperimentsChangeListener {
    fun changed()
}

@Service(Service.Level.APP)
class ExperimentsService : Disposable {

    private val logger = thisLogger()
    private val cache = AtomicReference<Map<String, ExperimentData>?>(null)
    private val countsCache = AtomicReference<Map<String, Int>?>(null)
    private val syncStatus = AtomicReference<SyncStatus>(SyncStatus.Idle)

    private val configCache = ConfigCache(File(PathManager.getSystemDir().toFile(), "brighterly-experiments"))

    init {
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        val configPaths = resolvedConfigs().map { it.path }.toSet()
                        if (events.any { it.file?.path in configPaths }) {
                            invalidateCache()
                        }
                    }
                },
            )
    }

    fun getExperiment(key: String): ExperimentData? = getAll()[key]

    fun getAll(): Map<String, ExperimentData> = cache.get() ?: loadAndCache()

    fun getCountsPerConfig(): Map<String, Int> =
        countsCache.get() ?: run { loadAndCache(); countsCache.get() ?: emptyMap() }

    fun syncStatus(): SyncStatus = syncStatus.get()

    fun invalidateCache() {
        cache.set(null)
        countsCache.set(null)
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).changed()
            ProjectManager.getInstance().openProjects.forEach { project ->
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }

    fun isConfigFile(path: String): Boolean = resolvedConfigs().any { it.path == path }

    private fun activeEnvironment() = ExperimentsSettings.getInstance().state.let { s ->
        s.environments.find { it.label == s.activeEnvironment }
    }

    /** The active environment's cached JSON, as a config source, if it has been fetched. */
    fun activeCacheConfig(): ConfigEntry? {
        val env = activeEnvironment() ?: return null
        return if (configCache.exists(env.label)) {
            ConfigEntry(configCache.cacheFile(env.label).path, ConfigType.JSON)
        } else null
    }

    /** Deprecated file-based sources — empty unless the user opts in via settings. */
    fun legacyFileConfigs(): List<ConfigEntry> {
        val settings = ExperimentsSettings.getInstance().state
        if (!settings.showFileConfigs) return emptyList()
        val configured = settings.configs.filter { it.path.isNotBlank() }
        return configured.ifEmpty { autoDetectedFileConfigs() }
    }

    private fun autoDetectedFileConfigs(): List<ConfigEntry> =
        ProjectManager.getInstance().openProjects.flatMap { project ->
            val base = project.basePath ?: return@flatMap emptyList()
            listOf(
                ConfigEntry("$base/config/experiments.php", ConfigType.PHP),
                ConfigEntry("$base/config/experiments.json", ConfigType.JSON),
            )
        }.filter { File(it.path).exists() }

    /** Active environment cache first (so URL wins), then any enabled legacy files. */
    fun resolvedConfigs(): List<ConfigEntry> = listOfNotNull(activeCacheConfig()) + legacyFileConfigs()

    fun resolvedConfigPath(): String = resolvedConfigs().firstOrNull()?.path ?: ""

    fun setActiveEnvironment(label: String) {
        ExperimentsSettings.getInstance().state.activeEnvironment = label
        syncActiveEnvironment()
    }

    /** True if the active env has a URL but no cache yet (used to sync once on startup). */
    fun shouldSyncOnStartup(): Boolean {
        val env = activeEnvironment() ?: return false
        return env.url.isNotBlank() && !configCache.exists(env.label)
    }

    /** Fetches the active environment's URL on a background thread and refreshes the index. */
    fun syncActiveEnvironment() {
        val env = activeEnvironment()
        if (env == null || env.url.isBlank()) {
            syncStatus.set(SyncStatus.Error("No URL for '${ExperimentsSettings.getInstance().state.activeEnvironment}'"))
            invalidateCache()
            return
        }
        syncStatus.set(SyncStatus.Syncing)
        invalidateCache() // shows ⏳ immediately
        ApplicationManager.getApplication().executeOnPooledThread {
            when (val result = RemoteConfigFetcher.fetch(env.url)) {
                is FetchResult.Success -> {
                    configCache.write(env.label, result.rawJson)
                    syncStatus.set(SyncStatus.Idle)
                }
                is FetchResult.Failure -> {
                    syncStatus.set(SyncStatus.Error(result.message))
                    notifyError(env.label, result.message)
                }
            }
            invalidateCache()
        }
    }

    private fun notifyError(envLabel: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("AB Tests")
            .createNotification(
                "AB Tests: failed to sync '$envLabel'",
                message,
                NotificationType.WARNING,
            )
            .notify(null)
    }

    private fun loadAndCache(): Map<String, ExperimentData> {
        val configs = resolvedConfigs()
        if (configs.isEmpty()) {
            cache.set(emptyMap())
            countsCache.set(emptyMap())
            return emptyMap()
        }

        val parsedPerConfig = mutableListOf<Map<String, ExperimentData>>()
        val perConfig = mutableMapOf<String, Int>()
        for (config in configs) {
            val file = File(config.path)
            if (!file.exists() || !file.isFile) {
                logger.warn("Experiments config not found: ${config.path}")
                perConfig[config.path] = 0
                continue
            }
            try {
                val parsed = when (config.type) {
                    ConfigType.PHP -> ExperimentsConfigParser.parse(file.readText())
                    ConfigType.JSON -> JsonExperimentsParser.parse(file.readText())
                }
                perConfig[config.path] = parsed.size
                parsedPerConfig.add(parsed)
            } catch (e: Exception) {
                logger.error("Failed to parse experiments config: ${config.path}", e)
                perConfig[config.path] = 0
            }
        }
        val merged = ExperimentsMerger.merge(parsedPerConfig)
        cache.set(merged)
        countsCache.set(perConfig)
        return merged
    }

    override fun dispose() {}

    companion object {
        fun getInstance(): ExperimentsService = service()

        @JvmField
        val TOPIC: Topic<ExperimentsChangeListener> =
            Topic.create("AB Tests experiments changed", ExperimentsChangeListener::class.java)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/service/ExperimentsService.kt
git commit -m "feat: environment-aware sources, background sync, and change topic in service"
```

---

## Task 8: Reference resolver — nested `experiments` keys

**Files:**
- Modify: `src/main/kotlin/com/brighterly/experiments/reference/ExperimentKeyReference.kt`

> Platform-bound PSI. Verified by compile + manual Ctrl+click smoke check (Task 11).

- [ ] **Step 1: Update `findInJsonFile`**

In `ExperimentKeyReference.kt`, replace the `findInJsonFile` method:

```kotlin
    private fun findInJsonFile(file: PsiFile): PsiElement? {
        val jsonFile = file as? JsonFile ?: return null
        val root = jsonFile.topLevelValue as? JsonObject ?: return null
        // Remote/cached configs nest experiments under "experiments"; legacy files are flat.
        val container = (root.findProperty("experiments")?.value as? JsonObject) ?: root
        return container.propertyList
            .find { it.name == experimentKey }
            ?.nameElement
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL (`JsonObject.findProperty` is part of `com.intellij.json.psi`, already imported via `JsonObject`).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/reference/ExperimentKeyReference.kt
git commit -m "feat: resolve experiment keys nested under 'experiments' in JSON configs"
```

---

## Task 9: Status bar widget — `CustomStatusBarWidget` + env/sync popup

**Files:**
- Modify: `src/main/kotlin/com/brighterly/experiments/statusbar/ExperimentsStatusBarWidgetFactory.kt`

> Platform-bound Swing/UI. Verified by compile + manual smoke check (Task 11).

- [ ] **Step 1: Replace the widget file**

Replace the entire contents of `src/main/kotlin/com/brighterly/experiments/statusbar/ExperimentsStatusBarWidgetFactory.kt`:

```kotlin
package com.brighterly.experiments.statusbar

import com.brighterly.experiments.service.ExperimentsChangeListener
import com.brighterly.experiments.service.ExperimentsService
import com.brighterly.experiments.service.SyncStatus
import com.brighterly.experiments.settings.ExperimentsSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent

class ExperimentsStatusBarWidgetFactory : StatusBarWidgetFactory {

    companion object {
        const val ID = "brighterly.experiments.status"
    }

    override fun getId(): String = ID
    override fun getDisplayName(): String = "AB Tests"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = ExperimentsStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class ExperimentsStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val label = JBLabel().apply {
        border = JBUI.Borders.empty(0, 6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = showPopup()
        })
    }

    override fun ID(): String = ExperimentsStatusBarWidgetFactory.ID

    override fun getComponent(): JComponent = label

    override fun install(statusBar: StatusBar) {
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(ExperimentsService.TOPIC, ExperimentsChangeListener {
                ApplicationManager.getApplication().invokeLater { updateText() }
            })
        updateText()
    }

    override fun dispose() {}

    private fun updateText() {
        val service = ExperimentsService.getInstance()
        val env = ExperimentsSettings.getInstance().state.activeEnvironment
        val status = service.syncStatus()
        label.text = when {
            status is SyncStatus.Syncing -> "Exp: $env  ⏳"
            status is SyncStatus.Error -> "Exp: $env  ⚠"
            service.getAll().isEmpty() -> "Exp: $env  ⚠"
            else -> "Exp: $env  ✓ ${service.getAll().size}"
        }
        label.toolTipText = when (status) {
            is SyncStatus.Error -> "AB Tests — ${status.message}. Click to switch environment or sync."
            else -> "AB Tests — click to switch environment or sync."
        }
    }

    private class Item(
        val label: String,
        val selected: Boolean = false,
        val separatorAbove: String? = null,
        val action: () -> Unit,
    )

    private fun showPopup() {
        val service = ExperimentsService.getInstance()
        val settings = ExperimentsSettings.getInstance().state
        val items = mutableListOf<Item>()

        settings.environments.forEach { env ->
            items.add(
                Item(env.label, selected = env.label == settings.activeEnvironment) {
                    service.setActiveEnvironment(env.label)
                },
            )
        }

        items.add(Item("↺  Sync now", separatorAbove = "") { service.syncActiveEnvironment() })

        if (settings.showFileConfigs) {
            val counts = service.getCountsPerConfig()
            var first = true
            service.legacyFileConfigs().forEach { entry ->
                val display = entry.path.replace("\\", "/").split("/").takeLast(2).joinToString("/")
                val exists = File(entry.path).exists()
                val countLabel = if (exists) "${counts[entry.path] ?: 0} exp" else "not found"
                items.add(
                    Item(
                        "$display  ·  ${entry.type}  ·  $countLabel (legacy)",
                        separatorAbove = if (first) "Legacy file configs (deprecated)" else null,
                    ) {
                        if (exists) {
                            LocalFileSystem.getInstance().findFileByPath(entry.path)?.let {
                                FileEditorManager.getInstance(project).openFile(it, true)
                            }
                        }
                    },
                )
                first = false
            }
        }

        items.add(
            Item("Settings…", separatorAbove = "") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "AB Tests")
            },
        )

        val step = object : BaseListPopupStep<Item>("AB Tests", items) {
            override fun getTextFor(value: Item): String = value.label
            override fun getIconFor(value: Item): Icon? =
                if (value.selected) AllIcons.Actions.Checked else null
            override fun getSeparatorAbove(value: Item): ListSeparator? =
                value.separatorAbove?.let { ListSeparator(it) }
            override fun onChosen(selectedValue: Item, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) selectedValue.action()
                return FINAL_CHOICE
            }
            override fun isSelectable(value: Item): Boolean = true
        }

        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(label)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/statusbar/ExperimentsStatusBarWidgetFactory.kt
git commit -m "feat: modernize status bar widget with environment switcher and sync"
```

---

## Task 10: Startup activity + `plugin.xml` (notification group, startup, description)

**Files:**
- Create: `src/main/kotlin/com/brighterly/experiments/startup/ExperimentsStartupActivity.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create the startup activity**

Create `src/main/kotlin/com/brighterly/experiments/startup/ExperimentsStartupActivity.kt`:

```kotlin
package com.brighterly.experiments.startup

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/** On project open, fetch the active environment once if it hasn't been cached yet. */
class ExperimentsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = ExperimentsService.getInstance()
        if (service.shouldSyncOnStartup()) {
            service.syncActiveEnvironment()
        }
    }
}
```

- [ ] **Step 2: Register the notification group and startup activity in `plugin.xml`**

In `src/main/resources/META-INF/plugin.xml`, inside `<extensions defaultExtensionNs="com.intellij">`, add (next to the other `applicationService` entries):

```xml
        <!-- Notification group for sync failures -->
        <notificationGroup id="AB Tests" displayType="BALLOON"/>

        <!-- Fetch the active environment on project open -->
        <postStartupActivity
            implementation="com.brighterly.experiments.startup.ExperimentsStartupActivity"/>
```

- [ ] **Step 3: Update the plugin description's "Config file support" and "Status bar" sections**

In `plugin.xml`, replace the `<h3>Config file support</h3>` block (the `<ul>` immediately after it) with:

```xml
        <h3>Experiment sources</h3>
        <ul>
            <li><b>Environments</b> — fetch experiments from per-environment URLs (development, staging, sandbox, production, local). Configure them in <b>Settings → Tools → AB Tests</b>.</li>
            <li><b>Switch &amp; sync</b> — pick the active environment and re-fetch from the status bar widget.</li>
            <li>Fetched configs are cached locally so navigation and Find Usages keep working.</li>
            <li><b>Local files (deprecated)</b> — legacy <code>experiments.php</code>/<code>experiments.json</code> support is off by default; enable it under <b>Settings → Tools → AB Tests</b>.</li>
        </ul>
```

And replace the `<h3>Status bar widget</h3>` `<ul>` block with:

```xml
        <h3>Status bar widget</h3>
        <ul>
            <li>Shows the active environment and loaded experiment count, e.g. <code>Exp: staging ✓ 16</code>.</li>
            <li>Click to switch environment, <b>Sync now</b>, or open settings.</li>
        </ul>
```

- [ ] **Step 4: Verify it builds**

Run: `./gradlew buildPlugin`
Expected: BUILD SUCCESSFUL (validates `plugin.xml` and all extension classes resolve).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/startup/ExperimentsStartupActivity.kt \
        src/main/resources/META-INF/plugin.xml
git commit -m "feat: sync active environment on startup; register notification group"
```

---

## Task 11: Settings UI — environments table, active selector, deprecation toggle

**Files:**
- Modify: `src/main/kotlin/com/brighterly/experiments/settings/ExperimentsSettingsConfigurable.kt`

> Platform-bound Swing UI. Verified by compile + manual smoke check below.

- [ ] **Step 1: Replace the configurable**

Replace the entire contents of `src/main/kotlin/com/brighterly/experiments/settings/ExperimentsSettingsConfigurable.kt`:

```kotlin
package com.brighterly.experiments.settings

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import com.brighterly.experiments.model.Environment
import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class ExperimentsSettingsConfigurable : Configurable {

    private var panel: JPanel? = null

    private val envRowsPanel = JPanel(GridBagLayout())
    private val envRows = mutableListOf<EnvRow>()
    private val activeEnvCombo = JComboBox<String>()

    private val showFilesCheckbox = JBCheckBox("Show experiments from local files (deprecated)")
    private val fileRowsPanel = JPanel(GridBagLayout())
    private val fileRows = mutableListOf<ConfigRow>()

    private class EnvRow(val labelField: JBTextField, val urlField: JBTextField)
    private class ConfigRow(val pathField: TextFieldWithBrowseButton, val typeCombo: JComboBox<ConfigType>)

    override fun getDisplayName(): String = "AB Tests"

    override fun createComponent(): JComponent {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)

        // --- Environments section ---
        root.add(leftLabel("Environments (experiments are fetched from the active environment's URL):"))
        root.add(JBScrollPane(envRowsPanel).apply { preferredSize = Dimension(640, 160) })

        val addEnvButton = JButton("+ Add Environment")
        addEnvButton.addActionListener { addEnvRow(Environment("", "")); refreshActiveCombo() }
        val activePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        activePanel.add(addEnvButton)
        activePanel.add(JBLabel("    Active environment:"))
        activePanel.add(activeEnvCombo)
        root.add(activePanel)

        root.add(Box.createVerticalStrut(12))

        // --- Deprecated file configs section ---
        root.add(leftLabel("Legacy file configs (deprecated):"))
        root.add(showFilesCheckbox)
        root.add(JBScrollPane(fileRowsPanel).apply { preferredSize = Dimension(640, 100) })
        val addFileButton = JButton("+ Add Config File")
        addFileButton.addActionListener { addFileRow(ConfigEntry("", ConfigType.PHP)) }
        val filePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        filePanel.add(addFileButton)
        root.add(filePanel)

        val outer = JPanel(BorderLayout())
        outer.add(root, BorderLayout.NORTH)
        panel = outer
        reset()
        return outer
    }

    private fun leftLabel(text: String): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(JBLabel(text)) }

    // --- environment rows ---

    private fun addEnvRow(env: Environment) {
        val labelField = JBTextField(env.label, 12)
        val urlField = JBTextField(env.url, 48)
        val removeButton = JButton("✕").apply { toolTipText = "Remove environment" }

        val row = EnvRow(labelField, urlField)
        envRows.add(row)

        val gbc = GridBagConstraints().apply {
            gridy = envRows.size - 1
            insets = JBUI.insets(2)
            fill = GridBagConstraints.HORIZONTAL
        }
        gbc.gridx = 0; gbc.weightx = 0.0; envRowsPanel.add(labelField, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; envRowsPanel.add(urlField, gbc)
        gbc.gridx = 2; gbc.weightx = 0.0; envRowsPanel.add(removeButton, gbc)

        labelField.document.addDocumentListenerSimple { refreshActiveCombo() }
        removeButton.addActionListener {
            envRows.remove(row)
            rebuildEnvRows()
            refreshActiveCombo()
        }

        envRowsPanel.revalidate(); envRowsPanel.repaint()
    }

    private fun rebuildEnvRows() {
        val snapshot = envRows.map { Environment(it.labelField.text, it.urlField.text) }
        envRowsPanel.removeAll(); envRows.clear()
        snapshot.forEach { addEnvRow(it) }
        envRowsPanel.revalidate(); envRowsPanel.repaint()
    }

    private fun refreshActiveCombo() {
        val selected = activeEnvCombo.selectedItem as? String
        activeEnvCombo.removeAllItems()
        envRows.map { it.labelField.text.trim() }.filter { it.isNotBlank() }.forEach { activeEnvCombo.addItem(it) }
        if (selected != null && (0 until activeEnvCombo.itemCount).any { activeEnvCombo.getItemAt(it) == selected }) {
            activeEnvCombo.selectedItem = selected
        }
    }

    // --- file rows ---

    private fun addFileRow(entry: ConfigEntry) {
        val pathField = TextFieldWithBrowseButton()
        pathField.text = entry.path
        pathField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle("Select Experiments Config File")
                .withDescription("Choose config/experiments.php or config/experiments.json"),
        )
        val typeCombo = JComboBox(ConfigType.values()).apply { selectedItem = entry.type }
        val removeButton = JButton("✕").apply { toolTipText = "Remove this config" }

        val row = ConfigRow(pathField, typeCombo)
        fileRows.add(row)

        val gbc = GridBagConstraints().apply {
            gridy = fileRows.size - 1
            insets = JBUI.insets(2)
            fill = GridBagConstraints.HORIZONTAL
        }
        gbc.gridx = 0; gbc.weightx = 1.0; fileRowsPanel.add(pathField, gbc)
        gbc.gridx = 1; gbc.weightx = 0.0; fileRowsPanel.add(typeCombo, gbc)
        gbc.gridx = 2; fileRowsPanel.add(removeButton, gbc)

        removeButton.addActionListener {
            fileRows.remove(row)
            rebuildFileRows()
        }
        fileRowsPanel.revalidate(); fileRowsPanel.repaint()
    }

    private fun rebuildFileRows() {
        val snapshot = fileRows.map { ConfigEntry(it.pathField.text, it.typeCombo.selectedItem as ConfigType) }
        fileRowsPanel.removeAll(); fileRows.clear()
        snapshot.forEach { addFileRow(it) }
        fileRowsPanel.revalidate(); fileRowsPanel.repaint()
    }

    override fun isModified(): Boolean {
        val state = ExperimentsSettings.getInstance().state

        val currentEnvs = envRows.map { Environment(it.labelField.text.trim(), it.urlField.text.trim()) }
            .filter { it.label.isNotBlank() }
        if (currentEnvs != state.environments) return true
        if ((activeEnvCombo.selectedItem as? String ?: "") != state.activeEnvironment) return true
        if (showFilesCheckbox.isSelected != state.showFileConfigs) return true

        val currentFiles = fileRows.map { ConfigEntry(it.pathField.text.trim(), it.typeCombo.selectedItem as ConfigType) }
            .filter { it.path.isNotBlank() }
        return currentFiles != state.configs.filter { it.path.isNotBlank() }
    }

    override fun apply() {
        val state = ExperimentsSettings.getInstance().state

        state.environments = envRows
            .map { Environment(it.labelField.text.trim(), it.urlField.text.trim()) }
            .filter { it.label.isNotBlank() }
            .toMutableList()
        state.activeEnvironment = (activeEnvCombo.selectedItem as? String) ?: state.environments.firstOrNull()?.label ?: "development"
        state.showFileConfigs = showFilesCheckbox.isSelected

        state.configs.clear()
        fileRows.forEach { row ->
            val path = row.pathField.text.trim()
            if (path.isNotBlank()) state.configs.add(ConfigEntry(path, row.typeCombo.selectedItem as ConfigType))
        }

        // Re-fetch the (possibly changed) active environment and re-index.
        ExperimentsService.getInstance().syncActiveEnvironment()
    }

    override fun reset() {
        val state = ExperimentsSettings.getInstance().state

        envRowsPanel.removeAll(); envRows.clear()
        state.environments.forEach { addEnvRow(Environment(it.label, it.url)) }
        refreshActiveCombo()
        activeEnvCombo.selectedItem = state.activeEnvironment

        showFilesCheckbox.isSelected = state.showFileConfigs

        fileRowsPanel.removeAll(); fileRows.clear()
        state.configs.forEach { addFileRow(ConfigEntry(it.path, it.type)) }

        envRowsPanel.revalidate(); envRowsPanel.repaint()
        fileRowsPanel.revalidate(); fileRowsPanel.repaint()
    }

    override fun disposeUIResources() { panel = null }
}

/** Minimal DocumentListener that fires the same callback on any change. */
private fun javax.swing.text.Document.addDocumentListenerSimple(onChange: () -> Unit) {
    addDocumentListener(object : javax.swing.event.DocumentListener {
        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    })
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Full build + run the test suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL; all tests from Tasks 2–5 pass.

- [ ] **Step 4: Manual smoke check in a sandbox IDE**

Run: `./gradlew runIde`
Verify:
- Status bar shows `Exp: development …`; after startup sync it shows `✓ <count>` (network permitting against the confirmed dev URL).
- Clicking the widget lists the 5 environments (✓ on development), `↺ Sync now`, and `Settings…`. Switching to `staging` triggers a sync and updates the label.
- **Settings → Tools → AB Tests** shows the 5 environments (editable), the active-environment combo, and the unchecked "Show experiments from local files (deprecated)" box with the legacy file rows below it.
- In a PHP/JS file, hovering an `exp-…` key from the fetched config shows the doc popup; Ctrl+click opens the cached JSON at that key; closed experiments render struck-through.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/brighterly/experiments/settings/ExperimentsSettingsConfigurable.kt
git commit -m "feat: settings UI for environments, active selection, and file deprecation toggle"
```

---

## Task 12: Docs + version bump

**Files:**
- Modify: `README.md`
- Modify: `gradle.properties`

- [ ] **Step 1: Update README feature list and configuration**

In `README.md`, replace the intro line and the **Configuration** section to lead with environments. Replace lines 3 and the `## Configuration` block:

Intro (replace line 3):

```markdown
PhpStorm plugin for working with A/B experiment keys. Experiments are fetched from per-environment config URLs (development, staging, sandbox, production, local); legacy local `config/experiments.php` / `experiments.json` files are still supported but deprecated.
```

Replace the `## Configuration` section body with:

```markdown
## Configuration

Go to **Settings → Tools → AB Tests**:

- **Environments** — each row is a label + a config URL (e.g. `https://storage.googleapis.com/brighterly-dev-configs/configs.json`). Pick the **active environment**; its URL is the source of experiments.
- **Active environment** can also be switched from the status bar widget, which offers **Sync now** to re-fetch.
- **Legacy file configs (deprecated)** — tick *Show experiments from local files (deprecated)* to also read `experiments.php` / `experiments.json` paths. Off by default. On a key conflict, the environment URL wins.

The remote JSON nests experiments under an `"experiments"` key; legacy local files keep `exp-*` keys at the top level. Both are supported.
```

- [ ] **Step 2: Bump the plugin version**

In `gradle.properties`, change:

```
pluginVersion=1.1.7
```
to
```
pluginVersion=1.2.0
```

- [ ] **Step 3: Verify the build is still green**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add README.md gradle.properties
git commit -m "docs: document environments as primary source; bump to 1.2.0"
```

---

## Self-Review (completed by plan author)

**Spec coverage:**
- §3 data model → Tasks 1, 2. §2 wrapper format → Tasks 3, 8. §4.1 fetcher → Task 6. §4.2 cache → Task 4. §4.3 service/sync/precedence → Tasks 5, 7. §4.4 widget → Task 9. §4.5 parser → Task 3. §4.6 reference → Task 8. §4.7 settings UI → Task 11. Startup sync (§4.3) + notifications (§6) → Task 10. Testing (§7) → Tasks 2–5. Docs/version → Task 12. No gaps.

**Placeholder scan:** No TBD/TODO; every code step shows full code; commands have expected output.

**Type consistency:** `FetchResult` (Task 4) is consumed unchanged in Tasks 5/7. `ExperimentsMerger.merge(List<Map<…>>)` (Task 5) is called in Task 7. `ExperimentsService.TOPIC` / `ExperimentsChangeListener` (Task 7) are subscribed in Task 9. `shouldSyncOnStartup()` / `syncActiveEnvironment()` / `setActiveEnvironment()` / `legacyFileConfigs()` / `getCountsPerConfig()` (Task 7) are all used in Tasks 9–11. `Environment(label, url)` (Task 1) is used consistently throughout. `ConfigCache.exists/cacheFile/write/applyFetch` names match between Tasks 4 and 7.

**Note for the executor:** an unrelated staged change exists (`AD src/.../annotator/ExperimentConfigAnnotator.kt`). Leave it alone — it is not part of this work and must stay out of these commits (use explicit path args in `git add`/`git commit` as written).
