package com.brighterly.experiments.findusages

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Enables Find Usages on experiment key string literals in the config file.
 *
 * When the user presses Alt+F7 on e.g. 'exp-23_pad-change-wording' in experiments.php,
 * IntelliJ calls ReferencesSearch.search(element) which finds all ExperimentKeyReference
 * instances whose resolve() returns this element — i.e. every place the key is used.
 */
class ExperimentFindUsagesProvider : FindUsagesProvider {

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        if (psiElement !is StringLiteralExpression) return false
        val key = psiElement.contents
        if (!key.startsWith("exp-")) return false
        // Only activate for elements that live in the experiments config file
        val configPath = ExperimentsService.getInstance().resolvedConfigPath()
        return configPath.isNotBlank() &&
            psiElement.containingFile?.virtualFile?.path == configPath
    }

    override fun getWordsScanner(): WordsScanner? = null // use IntelliJ's default text search

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = "experiment"

    override fun getDescriptiveName(element: PsiElement): String =
        (element as? StringLiteralExpression)?.contents ?: element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        (element as? StringLiteralExpression)?.contents ?: element.text
}
