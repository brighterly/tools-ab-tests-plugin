package com.brighterly.experiments.hints

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class ExperimentInlayHintsProviderTs : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val vFile = file.virtualFile
        if (vFile != null) {
            // Known extension: filter to ts/tsx/vue only; other extensions handled by JS provider
            val ext = vFile.extension
            if (ext != "ts" && ext != "tsx" && ext != "vue") return null
            if (ExperimentsService.getInstance().isConfigFile(vFile.path)) return null
        }
        // vFile == null → injected fragment (e.g. Vue <script lang="ts"> block); language scoping
        // ensures this provider only runs for TypeScript content, so proceed
        return ExperimentInlayHintsProvider.ExperimentHintsCollector()
    }
}
