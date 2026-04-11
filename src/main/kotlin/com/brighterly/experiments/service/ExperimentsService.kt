package com.brighterly.experiments.service

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import com.brighterly.experiments.model.ExperimentData
import com.brighterly.experiments.parser.ExperimentsConfigParser
import com.brighterly.experiments.parser.JsonExperimentsParser
import com.brighterly.experiments.settings.ExperimentsSettings
import com.brighterly.experiments.statusbar.ExperimentsStatusBarWidgetFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.WindowManager
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class ExperimentsService : Disposable {

    private val logger = thisLogger()
    private val cache = AtomicReference<Map<String, ExperimentData>?>(null)
    private val countsCache = AtomicReference<Map<String, Int>?>(null)

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

    /** Returns a map of config path → experiment count, populated alongside the main cache. */
    fun getCountsPerConfig(): Map<String, Int> = countsCache.get() ?: run { loadAndCache(); countsCache.get() ?: emptyMap() }

    fun invalidateCache() {
        cache.set(null)
        countsCache.set(null)
        ApplicationManager.getApplication().invokeLater {
            WindowManager.getInstance().allProjectFrames.forEach { frame ->
                frame.statusBar?.updateWidget(ExperimentsStatusBarWidgetFactory.ID)
            }
        }
    }

    /** Returns true if the given file path is one of the active config files. */
    fun isConfigFile(path: String): Boolean = resolvedConfigs().any { it.path == path }

    /**
     * Returns the active config entries: manually configured list, or auto-detected
     * config/experiments.php and config/experiments.json from all open project roots.
     */
    fun resolvedConfigs(): List<ConfigEntry> {
        val configured = ExperimentsSettings.getInstance().state.configs.filter { it.path.isNotBlank() }
        if (configured.isNotEmpty()) return configured

        return ProjectManager.getInstance().openProjects
            .flatMap { project ->
                val base = project.basePath ?: return@flatMap emptyList()
                listOf(
                    ConfigEntry("$base/config/experiments.php", ConfigType.PHP),
                    ConfigEntry("$base/config/experiments.json", ConfigType.JSON),
                )
            }
            .filter { File(it.path).exists() }
    }

    /**
     * Convenience for callers that only need to know about a single path (e.g. legacy checks).
     * Returns the first resolved config path, or blank if none.
     */
    fun resolvedConfigPath(): String = resolvedConfigs().firstOrNull()?.path ?: ""

    private fun loadAndCache(): Map<String, ExperimentData> {
        val configs = resolvedConfigs()
        if (configs.isEmpty()) return emptyMap()

        val merged = mutableMapOf<String, ExperimentData>()
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
                merged.putAll(parsed)
            } catch (e: Exception) {
                logger.error("Failed to parse experiments config: ${config.path}", e)
                perConfig[config.path] = 0
            }
        }
        cache.set(merged)
        countsCache.set(perConfig)
        return merged
    }

    override fun dispose() {}

    companion object {
        fun getInstance(): ExperimentsService = service()
    }
}
