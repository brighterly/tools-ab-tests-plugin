package com.brighterly.experiments.annotator

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.awt.Font

object ExperimentTextAttributes {
    val CLOSED_KEY: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "EXPERIMENT_CLOSED",
        TextAttributes(null, null, null, EffectType.STRIKEOUT, Font.PLAIN),
    )
}

class ExperimentAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val literal = element as? StringLiteralExpression ?: return
        val value = literal.contents
        if (!value.startsWith("exp-")) return

        val experiment = ExperimentsService.getInstance().getExperiment(value) ?: return
        if (!experiment.isClosed) return

        holder.newAnnotation(
            HighlightSeverity.INFORMATION,
            "Closed experiment — always uses branch: ${experiment.overrideBranch}",
        )
            .range(element)
            .textAttributes(ExperimentTextAttributes.CLOSED_KEY)
            .create()
    }
}
