package com.brighterly.experiments.findusages

import com.brighterly.experiments.reference.ExperimentKeyReference
import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Plugs into ReferencesSearch so that Alt+F7 on an experiment key in the config file
 * finds all usages across the project.
 *
 * Why this is needed: IntelliJ's default search uses the word index, which splits
 * "exp-23_pad-change-wording" on hyphens into separate words — so no candidates are
 * found. processUsagesInNonJavaFiles does a literal text search that matches the
 * whole key as-is.
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

        PsiSearchHelper.getInstance(project).processUsagesInNonJavaFiles(
            key,
            { file, startOffset, _ ->
                // Skip occurrences inside the config file itself (that's the definition)
                if (file.virtualFile?.path == configPath) return@processUsagesInNonJavaFiles true

                val leaf = file.findElementAt(startOffset)
                    ?: return@processUsagesInNonJavaFiles true
                val literal = leaf.parent as? StringLiteralExpression
                    ?: return@processUsagesInNonJavaFiles true
                if (literal.contents != key) return@processUsagesInNonJavaFiles true

                for (ref in literal.references) {
                    if (ref is ExperimentKeyReference) {
                        if (!consumer.process(ref)) return@processUsagesInNonJavaFiles false
                    }
                }
                true
            },
            globalScope,
        )
    }
}
