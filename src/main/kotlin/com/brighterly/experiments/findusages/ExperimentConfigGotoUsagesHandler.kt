package com.brighterly.experiments.findusages

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes Cmd+click on an experiment key in the config file show the ShowUsages popup.
 *
 * IntelliJ calls getGotoDeclarationTargets multiple times per click (once per registered
 * handler, potentially from multiple threads). The AtomicBoolean gate ensures exactly one
 * ShowUsages popup is scheduled — compareAndSet(false, true) succeeds only for the first
 * caller; all subsequent calls short-circuit. The flag is cleared in a finally block so
 * the next Cmd+click works normally.
 */
class ExperimentConfigGotoUsagesHandler : GotoDeclarationHandler {

    companion object {
        private val pending = AtomicBoolean(false)
    }

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

        // Ignore hover: getGotoDeclarationTargets is called for both Cmd+click navigation and
        // the ctrl-underline hover preview. Hover fires as MOUSE_MOVED with no button pressed.
        // We only exclude that specific case — everything else (click, keyboard shortcut, or
        // any non-mouse event IntelliJ emits during action dispatch) should proceed.
        val currentEvent = IdeEventQueue.getInstance().trueCurrentEvent
        if (currentEvent is MouseEvent && currentEvent.id == MouseEvent.MOUSE_MOVED) return null

        // Only the first caller wins; every other concurrent/subsequent call is a no-op.
        if (!pending.compareAndSet(false, true)) return PsiElement.EMPTY_ARRAY

        val project = element.project
        ApplicationManager.getApplication().invokeLater {
            try {
                val action = ActionManager.getInstance().getAction("ShowUsages") ?: return@invokeLater
                val ctx = SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(CommonDataKeys.EDITOR, editor)
                    .add(CommonDataKeys.PSI_ELEMENT, element)
                    .build()
                val event = AnActionEvent.createFromDataContext("GotoDeclaration", null, ctx)
                action.actionPerformed(event)
            } finally {
                pending.set(false)
            }
        }

        return PsiElement.EMPTY_ARRAY
    }
}
