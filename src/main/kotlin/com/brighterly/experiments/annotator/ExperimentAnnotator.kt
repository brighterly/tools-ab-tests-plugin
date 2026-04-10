package com.brighterly.experiments.annotator

import com.brighterly.experiments.service.ExperimentsService
import com.brighterly.experiments.util.experimentKey
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.ui.JBColor
import java.awt.Font

class ExperimentAnnotator : Annotator {

    private val closedAttributes = TextAttributes(JBColor.GRAY, null, JBColor.GRAY, EffectType.STRIKEOUT, Font.PLAIN)

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val value = element.experimentKey() ?: return
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
