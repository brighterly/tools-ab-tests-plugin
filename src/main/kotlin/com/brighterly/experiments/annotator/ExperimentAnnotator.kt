package com.brighterly.experiments.annotator

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.awt.Font

class ExperimentAnnotator : Annotator {

    // Gray foreground + gray strikethrough — the standard "disabled/closed" look
    private val closedAttributes = TextAttributes(JBColor.GRAY, null, JBColor.GRAY, EffectType.STRIKEOUT, Font.PLAIN)

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
            .enforcedTextAttributes(closedAttributes)
            .create()
    }
}
