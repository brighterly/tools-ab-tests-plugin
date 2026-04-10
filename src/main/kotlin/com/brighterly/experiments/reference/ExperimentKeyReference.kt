package com.brighterly.experiments.reference

import com.brighterly.experiments.service.ExperimentsService
import com.brighterly.experiments.settings.ExperimentsSettings
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
        val configPath = ExperimentsService.getInstance().resolvedConfigPath()
        if (configPath.isBlank()) return null

        val vFile = LocalFileSystem.getInstance().findFileByPath(configPath) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(vFile) ?: return null

        val text = psiFile.text
        val idx = text.indexOf("'$experimentKey'")
        if (idx < 0) return null

        // Return the StringLiteralExpression (not just the leaf token) so that
        // ReferencesSearch can match it by element equality for Find Usages
        val leaf = psiFile.findElementAt(idx + 1) ?: return null
        return leaf.parent as? StringLiteralExpression ?: leaf
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
