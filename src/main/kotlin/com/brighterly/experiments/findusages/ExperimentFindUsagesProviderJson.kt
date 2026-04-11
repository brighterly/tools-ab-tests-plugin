package com.brighterly.experiments.findusages

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement

class ExperimentFindUsagesProviderJson : FindUsagesProvider {

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        if (psiElement !is JsonStringLiteral) return false
        if (psiElement.parent !is JsonProperty) return false
        val key = psiElement.value
        if (!key.startsWith("exp-")) return false
        val path = psiElement.containingFile?.virtualFile?.path ?: return false
        return ExperimentsService.getInstance().isConfigFile(path)
    }

    override fun getWordsScanner(): WordsScanner? = null
    override fun getHelpId(psiElement: PsiElement): String? = null
    override fun getType(element: PsiElement): String = "experiment"

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? JsonStringLiteral)?.value ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        (element as? JsonStringLiteral)?.value ?: element.text
}
