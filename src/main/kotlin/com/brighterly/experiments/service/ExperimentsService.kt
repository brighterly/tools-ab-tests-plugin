package com.brighterly.experiments.service

import com.brighterly.experiments.model.ExperimentData
import com.brighterly.experiments.parser.ExperimentsConfigParser
import com.brighterly.experiments.settings.ExperimentsSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class ExperimentsService : Disposable {

    private val logger = thisLogger()
    private val cache = AtomicReference<Map<String, ExperimentData>?>(null)

    init {
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        val configPath = ExperimentsSettings.getInstance().state.configFilePath
                        if (configPath.isNotBlank() && events.any { it.file?.path == configPath }) {
                            invalidateCache()
                        }
                    }
                },
            )
    }

    fun getExperiment(key: String): ExperimentData? = getAll()[key]

    fun getAll(): Map<String, ExperimentData> = cache.get() ?: loadAndCache()

    fun invalidateCache() { cache.set(null) }

    private fun loadAndCache(): Map<String, ExperimentData> {
        val path = ExperimentsSettings.getInstance().state.configFilePath
        if (path.isBlank()) return emptyMap()

        val file = File(path)
        if (!file.exists() || !file.isFile) {
            logger.warn("Experiments config not found: $path")
            return emptyMap()
        }

        return try {
            ExperimentsConfigParser.parse(file.readText()).also { cache.set(it) }
        } catch (e: Exception) {
            logger.error("Failed to parse experiments config", e)
            emptyMap()
        }
    }

    override fun dispose() {} // MessageBusConnection is auto-disposed via Disposable parent

    companion object {
        fun getInstance(): ExperimentsService = service()
    }
}
