package com.brighterly.experiments.hints

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class ExperimentInlayHintsProviderJs : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val ext = file.virtualFile?.extension ?: return null
        if (ext != "js" && ext != "jsx") return null
        val filePath = file.virtualFile?.path ?: return ExperimentInlayHintsProvider.ExperimentHintsCollector()
        if (ExperimentsService.getInstance().isConfigFile(filePath)) return null
        return ExperimentInlayHintsProvider.ExperimentHintsCollector()
    }
}
