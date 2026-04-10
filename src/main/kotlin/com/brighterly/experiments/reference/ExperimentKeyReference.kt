package com.brighterly.experiments.reference

import com.brighterly.experiments.settings.ExperimentsSettings
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*

class ExperimentKeyReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val experimentKey: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, false) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val configPath = ExperimentsSettings.getInstance().state.configFilePath
        if (configPath.isBlank()) return null

        val vFile = LocalFileSystem.getInstance().findFileByPath(configPath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return null

        // Find the token for the quoted key string in the config file
        val text = psiFile.text
        val idx = text.indexOf("'$experimentKey'")
        if (idx < 0) return null
        return psiFile.findElementAt(idx + 1) // +1 lands inside the quotes
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
