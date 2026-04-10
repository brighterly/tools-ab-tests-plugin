package com.brighterly.experiments.settings

import com.brighterly.experiments.model.ConfigEntry
import com.brighterly.experiments.model.ConfigType
import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class ExperimentsSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private val rowsPanel = JPanel(GridBagLayout())
    private val rows = mutableListOf<ConfigRow>()

    private class ConfigRow(val pathField: TextFieldWithBrowseButton, val typeCombo: JComboBox<ConfigType>)

    override fun getDisplayName(): String = "AB Tests"

    override fun createComponent(): JComponent {
        rowsPanel.removeAll()
        rows.clear()

        val addButton = JButton("+ Add Config")
        addButton.addActionListener { addRow(ConfigEntry("", ConfigType.PHP)) }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        buttonPanel.add(addButton)

        val outer = JPanel(BorderLayout())
        outer.add(JBLabel("Config files (PHP or JSON):"), BorderLayout.NORTH)
        outer.add(JBScrollPane(rowsPanel), BorderLayout.CENTER)
        outer.add(buttonPanel, BorderLayout.SOUTH)

        panel = outer
        return outer
    }

    private fun addRow(entry: ConfigEntry) {
        val pathField = TextFieldWithBrowseButton()
        pathField.text = entry.path
        pathField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
                .withTitle("Select Experiments Config File")
                .withDescription("Choose config/experiments.php or config/experiments.json"),
        )

        val typeCombo = JComboBox(ConfigType.values())
        typeCombo.selectedItem = entry.type

        val removeButton = JButton("✕")
        removeButton.toolTipText = "Remove this config"

        val row = ConfigRow(pathField, typeCombo)
        rows.add(row)

        val gbc = GridBagConstraints().apply {
            gridy = rows.size - 1
            insets = Insets(2, 2, 2, 2)
            fill = GridBagConstraints.HORIZONTAL
        }

        gbc.gridx = 0; gbc.weightx = 1.0
        rowsPanel.add(pathField, gbc)

        gbc.gridx = 1; gbc.weightx = 0.0
        rowsPanel.add(typeCombo, gbc)

        gbc.gridx = 2
        rowsPanel.add(removeButton, gbc)

        removeButton.addActionListener {
            val idx = rows.indexOf(row)
            if (idx >= 0) {
                rows.removeAt(idx)
                rebuildRows()
            }
        }

        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun rebuildRows() {
        val snapshot = rows.toList()
        rowsPanel.removeAll()
        rows.clear()
        snapshot.forEach { r -> addRow(ConfigEntry(r.pathField.text, r.typeCombo.selectedItem as ConfigType)) }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    override fun isModified(): Boolean {
        val current = ExperimentsSettings.getInstance().state.configs
        if (rows.size != current.size) return true
        return rows.zip(current).any { (row, entry) ->
            row.pathField.text != entry.path || row.typeCombo.selectedItem != entry.type
        }
    }

    override fun apply() {
        val state = ExperimentsSettings.getInstance().state
        state.configs.clear()
        rows.forEach { row ->
            val path = row.pathField.text.trim()
            if (path.isNotBlank()) {
                state.configs.add(ConfigEntry(path, row.typeCombo.selectedItem as ConfigType))
            }
        }
        ExperimentsService.getInstance().invalidateCache()
    }

    override fun reset() {
        rowsPanel.removeAll()
        rows.clear()
        ExperimentsSettings.getInstance().state.configs.forEach { addRow(it) }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    override fun disposeUIResources() { panel = null }
}
