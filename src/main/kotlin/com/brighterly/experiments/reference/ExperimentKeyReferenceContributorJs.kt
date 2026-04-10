package com.brighterly.experiments.reference

import com.brighterly.experiments.service.ExperimentsService
import com.brighterly.experiments.util.experimentKey
import com.brighterly.experiments.util.experimentKeyTextRange
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class ExperimentKeyReferenceContributorJs : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(JSLiteralExpression::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val value = element.experimentKey() ?: return emptyArray()
                    if (ExperimentsService.getInstance().getExperiment(value) == null) return emptyArray()
                    val range = element.experimentKeyTextRange() ?: return emptyArray()
                    return arrayOf(ExperimentKeyReference(element, range, value))
                }
            },
            PsiReferenceRegistrar.HIGHER_PRIORITY,
        )
    }
}
