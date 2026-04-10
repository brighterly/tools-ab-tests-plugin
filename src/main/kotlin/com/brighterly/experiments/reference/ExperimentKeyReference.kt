package com.brighterly.experiments.reference

import com.brighterly.experiments.model.ConfigType
import com.brighterly.experiments.service.ExperimentsService
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ExperimentKeyReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val experimentKey: String,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, false) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val configs = ExperimentsService.getInstance().resolvedConfigs()

        for (config in configs) {
            val vFile = LocalFileSystem.getInstance().findFileByPath(config.path) ?: continue
            val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: continue
            val found = when (config.type) {
                ConfigType.PHP -> findInPhpFile(psiFile)
                ConfigType.JSON -> findInJsonFile(psiFile)
            }
            if (found != null) return found
        }
        return null
    }

    private fun findInPhpFile(file: PsiFile): PsiElement? {
        val text = file.text
        val idx = text.indexOf("'$experimentKey'")
        if (idx < 0) return null
        val leaf = file.findElementAt(idx + 1) ?: return null
        return leaf.parent as? StringLiteralExpression ?: leaf
    }

    private fun findInJsonFile(file: PsiFile): PsiElement? {
        val jsonFile = file as? JsonFile ?: return null
        val root = jsonFile.topLevelValue as? JsonObject ?: return null
        return root.propertyList
            .find { it.name == experimentKey }
            ?.nameElement
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
