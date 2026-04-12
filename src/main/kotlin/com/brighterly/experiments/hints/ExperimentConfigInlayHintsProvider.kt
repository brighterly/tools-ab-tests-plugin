package com.brighterly.experiments.hints

import com.brighterly.experiments.parser.ExperimentsConfigParser
import com.brighterly.experiments.parser.JsonExperimentsParser
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Shows an inline ⚠ hint after experiment keys in config files when branch
 * percentages don't sum to 100.
 *
 * "Is this a config file?" is determined by content: if the parser finds no
 * experiment entries the file is skipped, which avoids brittle path-equality
 * checks that can fail due to symlinks or settings mismatches.
 *
 * Parsing runs once in createCollector (per hints pass); the collector is O(1).
 */
class ExperimentConfigInlayHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val vFile = file.virtualFile ?: return null

        val experiments = when (vFile.extension) {
            "php" -> runCatching { ExperimentsConfigParser.parse(file.text) }.getOrNull()
            "json" -> runCatching { JsonExperimentsParser.parse(file.text) }.getOrNull()
            else -> null
        } ?: return null

        // Not a config file — no experiment entries found
        if (experiments.isEmpty()) return null

        val badSums: Map<String, Int> = experiments
            .filterValues { !it.isClosed && it.branches.values.sum() != 100 }
            .mapValues { (_, exp) -> exp.branches.values.sum() }

        if (badSums.isEmpty()) return null
        return ConfigHintsCollector(badSums)
    }

    private class ConfigHintsCollector(
        private val badSums: Map<String, Int>,
    ) : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            val key = when {
                element is StringLiteralExpression ->
                    element.contents.takeIf { it.startsWith("exp-") }

                element is JsonStringLiteral && element.parent is JsonProperty ->
                    element.value.takeIf { it.startsWith("exp-") }

                else -> null
            } ?: return

            val sum = badSums[key] ?: return

            sink.addPresentation(
                position = InlineInlayPosition(element.textRange.endOffset, relatedToPrevious = true),
                payloads = null,
                tooltip = "Branch percentages sum to $sum% (expected 100%)",
                hasBackground = false,
            ) {
                text(" ⚠ ${sum}%")
            }
        }
    }
}
