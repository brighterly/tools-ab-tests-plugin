package com.brighterly.experiments.findusages

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Makes Cmd+click on an experiment key in the config file navigate to all usages.
 *
 * The config key is a declaration, not a reference — there is no "definition" to go to.
 * Instead we collect every ExperimentKeyReference across the project and hand them back
 * as navigation targets. IntelliJ shows a "Choose Target" popup for multiple hits, or
 * navigates directly for a single match.
 */
class ExperimentConfigGotoUsagesHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor,
    ): Array<PsiElement>? {
        val leaf = sourceElement ?: return null
        val element = leaf.parent as? StringLiteralExpression ?: return null
        val key = element.contents
        if (!key.startsWith("exp-")) return null

        val configPath = ExperimentsService.getInstance().resolvedConfigPath()
        if (configPath.isBlank() || element.containingFile?.virtualFile?.path != configPath) return null

        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)
        val targets = ReferencesSearch.search(element, scope)
            .findAll()
            .map { it.element }
            .toTypedArray<PsiElement>()

        return targets.ifEmpty { null }
    }
}
