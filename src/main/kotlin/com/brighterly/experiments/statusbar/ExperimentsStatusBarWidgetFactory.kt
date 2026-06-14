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
