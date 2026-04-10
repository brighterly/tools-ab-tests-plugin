package com.brighterly.experiments.findusages

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Makes Cmd+click on an experiment key in the config file show the ShowUsages popup —
 * the same rich panel (file name, line, code snippet) that appears when Cmd+clicking
 * on a PHP function declaration.
 *
 * The key is a declaration, not a reference, so there is no "definition" to navigate to.
 * Instead we programmatically invoke the "ShowUsages" action (Cmd+Alt+F7) with the
 * experiment key element as the PSI_ELEMENT in the data context. IntelliJ chains through
 * ExperimentFindUsagesProvider → ExperimentReferencesSearcher and renders the popup.
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

        // Defer to the next EDT cycle so GotoDeclaration completes first, then the
        // ShowUsages popup opens cleanly without conflicting with the current action.
        ApplicationManager.getApplication().invokeLater {
            val action = ActionManager.getInstance().getAction("ShowUsages") ?: return@invokeLater
            val ctx = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, editor)
                .add(CommonDataKeys.PSI_ELEMENT, element)
                .build()
            val event = AnActionEvent.createFromDataContext("GotoDeclaration", null, ctx)
            action.actionPerformed(event)
        }

        // Return EMPTY_ARRAY to suppress plain "Choose Target" navigation.
        // ShowUsages popup will appear via invokeLater.
        return PsiElement.EMPTY_ARRAY
    }
}
