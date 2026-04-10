package com.brighterly.experiments.documentation

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ExperimentDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val key = resolveKey(element, originalElement) ?: return null
        val experiment = ExperimentsService.getInstance().getExperiment(key) ?: return null

        return buildString {
            append("<html><body>")
            append("<b>Experiment:</b> ${experiment.key}<br/><br/>")

            append("<b>Branches:</b><br/>")
            experiment.branches.forEach { (branch, pct) ->
                append("&nbsp;&nbsp;<tt>$branch</tt>: <b>$pct%</b><br/>")
            }

            if (experiment.isClosed) {
                append("<br/><font color='#CC0000'><b>⚠ Closed</b></font>")
                append(" — always uses branch: <tt>${experiment.overrideBranch}</tt><br/>")
            }

            if (experiment.startDate != null) {
                append("<br/><b>Start date:</b> ${experiment.startDate}<br/>")
            }

            append("<br/><b>Distribution:</b> [${experiment.distributionLabel}]")
            append("</body></html>")
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? {
        val key = resolveKey(element, originalElement) ?: return null
        val experiment = ExperimentsService.getInstance().getExperiment(key) ?: return null
        val closed = if (experiment.isClosed) " ⚠ closed" else ""
        return "${experiment.key} [${experiment.distributionLabel}]$closed"
    }

    private fun resolveKey(element: PsiElement, originalElement: PsiElement?): String? {
        val literal = (originalElement?.parent as? StringLiteralExpression)
            ?: (element as? StringLiteralExpression)
            ?: return null
        val value = literal.contents
        return if (value.startsWith("exp-")) value else null
    }
}
