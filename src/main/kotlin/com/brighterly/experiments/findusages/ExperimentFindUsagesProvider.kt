package com.brighterly.experiments.findusages

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ExperimentFindUsagesProvider : FindUsagesProvider {

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        if (psiElement !is StringLiteralExpression) return false
        val key = psiElement.contents
        if (!key.startsWith("exp-")) return false
        val path = psiElement.containingFile?.virtualFile?.path ?: return false
        return ExperimentsService.getInstance().isConfigFile(path)
    }

    override fun getWordsScanner(): WordsScanner? = null
    override fun getHelpId(psiElement: PsiElement): String? = null
    override fun getType(element: PsiElement): String = "experiment"

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? StringLiteralExpression)?.contents ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        (element as? StringLiteralExpression)?.contents ?: element.text
}
