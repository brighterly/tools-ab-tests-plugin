package com.brighterly.experiments.statusbar

import com.brighterly.experiments.service.ExperimentsService
import com.brighterly.experiments.settings.ExperimentsSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.awt.Component

class ExperimentsStatusBarWidgetFactory : StatusBarWidgetFactory {

    companion object {
        const val ID = "brighterly.experiments.status"
    }

    override fun getId(): String = ID
    override fun getDisplayName(): String = "AB Tests"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = ExperimentsStatusBarWidget()
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

// getPresentation() is deprecated — 2026.1+ discovers TextPresentation via instanceof
class ExperimentsStatusBarWidget : StatusBarWidget, StatusBarWidget.TextPresentation {

    override fun ID(): String = ExperimentsStatusBarWidgetFactory.ID
    override fun install(statusBar: StatusBar) {}
    override fun dispose() {}
    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getText(): String {
        val service = ExperimentsService.getInstance()
        val path = service.resolvedConfigPath()
        return when {
            path.isBlank() -> "Exp ⚠"
            service.getAll().isEmpty() -> "Exp ⚠"
            else -> "Exp ✓ ${service.getAll().size}"
        }
    }

    override fun getTooltipText(): String {
        val service = ExperimentsService.getInstance()
        val configuredPath = ExperimentsSettings.getInstance().state.configFilePath
        val resolvedPath = service.resolvedConfigPath()
        val all = service.getAll()

        return when {
            resolvedPath.isBlank() ->
                "AB Tests: config not found. Set path in Settings → Tools → AB Tests"
            all.isEmpty() ->
                "AB Tests: failed to parse or empty config at $resolvedPath"
            configuredPath.isBlank() ->
                "AB Tests: ${all.size} experiments loaded (auto-detected: $resolvedPath)"
            else ->
                "AB Tests: ${all.size} experiments loaded from $resolvedPath"
        }
    }
}
