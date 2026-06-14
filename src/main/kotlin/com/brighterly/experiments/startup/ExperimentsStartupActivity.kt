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
