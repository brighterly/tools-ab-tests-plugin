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
import com.intellij.openapi.vfs.LocalFileSystem
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
            val result = RemoteConfigFetcher.fetch(env.url)
            val cacheFile = configCache.applyFetch(env.label, result)
            when (result) {
                is FetchResult.Success -> {
                    // Make the freshly-written cache file visible / up-to-date in the VFS so
                    // Ctrl+click and Find Usages resolve against current PSI. A synchronous
                    // refresh is correct here because we are off the EDT.
                    cacheFile?.let { LocalFileSystem.getInstance().refreshAndFindFileByPath(it.path) }
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
