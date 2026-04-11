package com.brighterly.experiments.statusbar

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.Icon

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

class ExperimentsStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    override fun ID(): String = ExperimentsStatusBarWidgetFactory.ID

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {}
    override fun dispose() {}
    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getText(): String {
        val service = ExperimentsService.getInstance()
        val all = service.getAll()
        val configs = service.resolvedConfigs()

        return when {
            service.resolvedConfigs().isEmpty() -> "Exp ⚠"
            all.isEmpty() -> "Exp ⚠"
            else -> "Exp ✓ ${all.size} in ${configs.size} source files"
        }
    }

    override fun getTooltipText(): String = "AB Tests — click to see configs"

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { mouseEvent ->
        val service = ExperimentsService.getInstance()
        val configs = service.resolvedConfigs()
        val counts = service.getCountsPerConfig()

        data class Item(val label: String, val action: () -> Unit)

        val items = if (configs.isEmpty()) {
            listOf(Item("No config found — open Settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "AB Tests")
            })
        } else {
            val configItems = configs.map { entry ->
                val segments = entry.path.replace("\\", "/").split("/")
                val display = segments.takeLast(2).joinToString("/")
                val count = counts[entry.path] ?: 0
                val exists = File(entry.path).exists()
                val countLabel = if (exists) "$count exp" else "not found"
                Item("$display  ·  ${entry.type}  ·  $countLabel") {
                    if (exists) {
                        val vFile = LocalFileSystem.getInstance().findFileByPath(entry.path)
                        if (vFile != null) FileEditorManager.getInstance(project).openFile(vFile, true)
                    }
                }
            } + Item("↺  Reload configs") {
                service.invalidateCache()
            }
            configItems
        }

        val step = object : com.intellij.openapi.ui.popup.util.BaseListPopupStep<Item>("AB Tests", items) {
            override fun getTextFor(value: Item): String = value.label
            override fun getIconFor(value: Item): Icon? = null
            override fun onChosen(selectedValue: Item, finalChoice: Boolean): com.intellij.openapi.ui.popup.PopupStep<*>? {
                if (finalChoice) selectedValue.action()
                return FINAL_CHOICE
            }
            override fun isSelectable(value: Item): Boolean = true
        }

        val popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance().createListPopup(step)
        popup.showUnderneathOf(mouseEvent.component)
    }
}
