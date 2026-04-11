package com.brighterly.experiments.hints

import com.brighterly.experiments.service.ExperimentsService
import com.brighterly.experiments.util.experimentKey
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class ExperimentInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val filePath = file.virtualFile?.path ?: return ExperimentHintsCollector()
        if (ExperimentsService.getInstance().isConfigFile(filePath)) return null
        return ExperimentHintsCollector()
    }

    internal class ExperimentHintsCollector : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            val value = element.experimentKey() ?: return
            val experiment = ExperimentsService.getInstance().getExperiment(value) ?: return

            val label = if (experiment.isClosed) {
                "[${experiment.overrideBranch}]"
            } else {
                "[${experiment.distributionLabel}]"
            }

            sink.addPresentation(
                position = InlineInlayPosition(element.textRange.endOffset, relatedToPrevious = true),
                payloads = null,
                tooltip = null,
                hasBackground = false,
            ) {
                text(" $label")
            }
        }
    }
}
