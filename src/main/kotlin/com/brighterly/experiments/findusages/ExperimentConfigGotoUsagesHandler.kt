package com.brighterly.experiments.findusages

import com.brighterly.experiments.service.ExperimentsService
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Makes Cmd+click on an experiment key in the config file show the ShowUsages popup.
 *
 * Two guards:
 *
 * 1. mouseDown — a Toolkit AWT listener tracks MOUSE_PRESSED / MOUSE_RELEASED globally.
 *    Hover (no button held) sets this to false, so we return null and do nothing.
 *
 * 2. lastShownAt — timestamp cooldown (500 ms) prevents multiple popups when IntelliJ
 *    calls getGotoDeclarationTargets more than once for the same click. A boolean pending
 *    flag is unreliable because invokeLater may complete and reset it before a second call
 *    arrives on the same EDT cycle.
 */
class ExperimentConfigGotoUsagesHandler : GotoDeclarationHandler {

    companion object {
        private val mouseDown = AtomicBoolean(false)
        private val lastShownAt = AtomicLong(0L)
        private const val COOLDOWN_MS = 500L

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

        // Resolve the config key element — works for both PHP and JSON config files
        val (element, key) = resolveConfigKeyElement(leaf) ?: return null

        if (!ExperimentsService.getInstance().isConfigFile(
                element.containingFile?.virtualFile?.path ?: return null
            )
        ) return null

        if (!mouseDown.get()) return null

        // Cooldown: reject calls within 500 ms of the last shown popup.
        val now = System.currentTimeMillis()
        val last = lastShownAt.get()
        if (now - last < COOLDOWN_MS || !lastShownAt.compareAndSet(last, now)) return PsiElement.EMPTY_ARRAY

        val project = element.project
        ApplicationManager.getApplication().invokeLater {
            val action = ActionManager.getInstance().getAction("ShowUsages") ?: return@invokeLater
            val ctx = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, editor)
                .add(CommonDataKeys.PSI_ELEMENT, element)
                .build()
            val event = AnActionEvent.createEvent(ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
            ActionUtil.performActionDumbAwareWithCallbacks(action, event)
        }

        return PsiElement.EMPTY_ARRAY
    }

    /**
     * Returns (declarationElement, experimentKey) if the leaf is inside a config key,
     * null otherwise. Handles both PHP StringLiteralExpression and JSON JsonStringLiteral.
     */
    private fun resolveConfigKeyElement(leaf: PsiElement): Pair<PsiElement, String>? {
        // PHP config: 'exp-foo' => [...]
        val phpLiteral = leaf.parent as? StringLiteralExpression
        if (phpLiteral != null) {
            val key = phpLiteral.contents
            if (key.startsWith("exp-")) return phpLiteral to key
        }

        // JSON config: "exp-foo": { ... }  — the JsonStringLiteral must be a property key
        val jsonLiteral = leaf.parent as? JsonStringLiteral
        if (jsonLiteral != null && jsonLiteral.parent is JsonProperty) {
            val key = jsonLiteral.value
            if (key.startsWith("exp-")) return jsonLiteral to key
        }

        return null
    }
}
