package com.brighterly.experiments.reference

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ExperimentKeyReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val literal = element as? StringLiteralExpression ?: return emptyArray()
                    val value = literal.contents
                    if (!value.startsWith("exp-")) return emptyArray()
                    if (ExperimentsService.getInstance().getExperiment(value) == null) return emptyArray()

                    // range inside quotes: offset 1 to value.length + 1
                    return arrayOf(ExperimentKeyReference(element, TextRange(1, value.length + 1), value))
                }
            },
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )
    }
}
