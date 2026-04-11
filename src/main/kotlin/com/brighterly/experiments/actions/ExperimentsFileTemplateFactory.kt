package com.brighterly.experiments.actions

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import com.intellij.json.JsonFileType
import com.intellij.openapi.util.IconLoader
import com.jetbrains.php.lang.PhpFileType

class ExperimentsFileTemplateFactory : FileTemplateGroupDescriptorFactory {

    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val icon = IconLoader.getIcon("/icons/newExperimentsConfig.svg", ExperimentsFileTemplateFactory::class.java)
        val group = FileTemplateGroupDescriptor("Experiments", icon)

        group.addTemplate(FileTemplateDescriptor("Experiments PHP Config.php", PhpFileType.INSTANCE.icon))
        group.addTemplate(FileTemplateDescriptor("Experiments JSON Config.json", JsonFileType.INSTANCE.icon))

        return group
    }
}
