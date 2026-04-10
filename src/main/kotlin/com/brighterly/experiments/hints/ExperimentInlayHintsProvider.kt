package com.brighterly.experiments.hints

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ExperimentInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector =
        ExperimentHintsCollector()

    private class ExperimentHintsCollector : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            val literal = element as? StringLiteralExpression ?: return
            val value = literal.contents
            if (!value.startsWith("exp-")) return

            val experiment = ExperimentsService.getInstance().getExperiment(value) ?: return

            sink.addPresentation(
                position = InlineInlayPosition(element.textRange.endOffset, relatedToPrevious = true),
                hasBackground = false,
            ) {
                text(" [${experiment.distributionLabel}]")
            }
        }
    }
}
