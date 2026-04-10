package com.brighterly.experiments.settings

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class ExperimentsSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private val pathField = TextFieldWithBrowseButton()

    override fun getDisplayName(): String = "Brighterly Experiments"

    override fun createComponent(): JComponent {
        pathField.addBrowseFolderListener(
            "Select Experiments Config File",
            "Choose the PHP experiments config file (e.g. config/experiments.php)",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("php"),
        )
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Config file path:"), pathField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean =
        pathField.text != ExperimentsSettings.getInstance().state.configFilePath

    override fun apply() {
        ExperimentsSettings.getInstance().state.configFilePath = pathField.text
        ExperimentsService.getInstance().invalidateCache()
    }

    override fun reset() {
        pathField.text = ExperimentsSettings.getInstance().state.configFilePath
    }

    override fun disposeUIResources() { panel = null }
}
