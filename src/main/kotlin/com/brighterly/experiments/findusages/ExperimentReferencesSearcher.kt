package com.brighterly.experiments.findusages

import com.brighterly.experiments.reference.ExperimentKeyReference
import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Drives Find Usages for experiment keys defined in experiments.php.
 *
 * Why the previous approach (processUsagesInNonJavaFiles) failed:
 * that API is designed for Java qualified-name lookups and splits strings on
 * hyphens — so "exp-23_pad-change-wording" was never found.
 *
 * This implementation uses FileTypeIndex to get all PHP files in scope, then
 * walks each file's PSI tree looking for exact-match StringLiteralExpressions.
 * Our ExperimentKeyReference is then collected from each match.
 */
class ExperimentReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val element = parameters.elementToSearch as? StringLiteralExpression ?: return
        val key = element.contents
        if (!key.startsWith("exp-")) return

        val configPath = ExperimentsService.getInstance().resolvedConfigPath()
        if (configPath.isBlank() || element.containingFile?.virtualFile?.path != configPath) return

        val project = parameters.project
        val globalScope = parameters.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.projectScope(project)
        val manager = PsiManager.getInstance(project)

        FileTypeIndex.getFiles(PhpFileType.INSTANCE, globalScope).forEach { vFile ->
            if (vFile.path == configPath) return@forEach          // skip the definition file
            val psiFile = manager.findFile(vFile) ?: return@forEach

            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(visited: PsiElement) {
                    if (visited is StringLiteralExpression && visited.contents == key) {
                        for (ref in visited.references) {
                            if (ref is ExperimentKeyReference) consumer.process(ref)
                        }
                    }
                    super.visitElement(visited)
                }
            })
        }
    }
}
