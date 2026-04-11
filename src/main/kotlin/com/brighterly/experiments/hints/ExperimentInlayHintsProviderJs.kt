package com.brighterly.experiments.hints

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class ExperimentInlayHintsProviderJs : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val vFile = file.virtualFile
        if (vFile != null) {
            // Known extension: filter to js/jsx/vue only; other extensions handled by TS provider
            val ext = vFile.extension
            if (ext != "js" && ext != "jsx" && ext != "vue") return null
            if (ExperimentsService.getInstance().isConfigFile(vFile.path)) return null
        }
        // vFile == null → injected fragment (e.g. Vue <script> block); language scoping already
        // ensures this provider only runs for JavaScript content, so proceed
        return ExperimentInlayHintsProvider.ExperimentHintsCollector()
    }
}
