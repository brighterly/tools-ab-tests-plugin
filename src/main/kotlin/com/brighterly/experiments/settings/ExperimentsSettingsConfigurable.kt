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
