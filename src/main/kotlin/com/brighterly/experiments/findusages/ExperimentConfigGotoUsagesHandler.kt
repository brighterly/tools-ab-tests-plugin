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
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes Cmd+click on an experiment key in the config file show the ShowUsages popup.
 *
 * Two guards:
 *
 * 1. mouseDown — a Toolkit AWT listener tracks MOUSE_PRESSED / MOUSE_RELEASED globally.
 *    Hover (no button held) sets this to false, so we return null and do nothing.
 *    This is more reliable than checking IdeEventQueue.trueCurrentEvent, which may have
 *    already advanced past the original mouse event by the time the handler fires.
 *
 * 2. pending — AtomicBoolean ensures only one ShowUsages popup is scheduled per click,
 *    even if IntelliJ calls getGotoDeclarationTargets concurrently from multiple handlers.
 */
class ExperimentConfigGotoUsagesHandler : GotoDeclarationHandler {

    companion object {
        private val mouseDown = AtomicBoolean(false)
        private val pending = AtomicBoolean(false)

        init {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                { event ->
                    val me = event as? MouseEvent ?: return@addAWTEventListener
                    when (me.id) {
                        MouseEvent.MOUSE_PRESSED -> mouseDown.set(true)
                        MouseEvent.MOUSE_RELEASED -> mouseDown.set(false)
                    }
                },
                AWTEvent.MOUSE_EVENT_MASK,
            )
        }
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

        // Hover: no mouse button is pressed — skip entirely.
        if (!mouseDown.get()) return null

        // Only the first caller wins; all concurrent/subsequent calls are no-ops.
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
