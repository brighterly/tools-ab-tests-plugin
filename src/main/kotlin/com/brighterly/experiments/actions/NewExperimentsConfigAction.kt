package com.brighterly.experiments.actions

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiDirectory
import com.intellij.ui.popup.list.ListPopupImpl

class NewExperimentsConfigAction :
    DumbAwareAction("Experiments Config", "Create a new experiments config file (PHP or JSON)", null) {

    private enum class Kind(val label: String, val extension: String, val templateName: String) {
        PHP("PHP", "php", "Experiments PHP Config"),
        JSON("JSON", "json", "Experiments JSON Config"),
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val view = e.getData(LangDataKeys.IDE_VIEW)
        e.presentation.isEnabledAndVisible = view?.directories?.isNotEmpty() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val view = e.getData(LangDataKeys.IDE_VIEW) ?: return
        val dir = view.orChooseDirectory ?: return

        val name = com.intellij.openapi.ui.Messages.showInputDialog(
            project,
            "File name (without extension):",
            "New Experiments Config",
            null,
            "experiments",
            null,
        ) ?: return
        val baseName = name.trim().ifEmpty { "experiments" }

        val step = object : BaseListPopupStep<Kind>("Choose Format", Kind.entries) {
            override fun getTextFor(value: Kind): String = value.label

            override fun onChosen(selectedValue: Kind, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    ApplicationManager.getApplication().invokeLater {
                        createConfig(project, dir, selectedValue, baseName)
                    }
                }
                return FINAL_CHOICE
            }
        }

        ListPopupImpl(project, step).showCenteredInCurrentWindow(project)
    }

    private fun createConfig(project: Project, dir: PsiDirectory, kind: Kind, baseName: String) {
        val content = resolveContent(project, kind)
        WriteCommandAction.runWriteCommandAction(project, "Create ${kind.templateName}", null, {
            val vDir = dir.virtualFile
            val fileName = "$baseName.${kind.extension}"
            val file = vDir.findChild(fileName) ?: vDir.createChildData(this@NewExperimentsConfigAction, fileName)
            file.setBinaryContent(content.toByteArray(Charsets.UTF_8))
            FileEditorManager.getInstance(project).openFile(file, true)
        })
    }

    private fun resolveContent(project: Project, kind: Kind): String {
        // Prefer user-edited template from Settings → File and Code Templates
        val mgr = FileTemplateManager.getInstance(project)
        val template = runCatching { mgr.getTemplate(kind.templateName) }.getOrNull()
            ?: runCatching { mgr.getInternalTemplate(kind.templateName) }.getOrNull()
        if (template != null) {
            runCatching { return template.getText(mgr.defaultProperties) }
        }
        // Fall back to bundled resource file
        val resource = javaClass.classLoader
            .getResourceAsStream("fileTemplates/${kind.templateName}.${kind.extension}")
        if (resource != null) {
            runCatching { return resource.bufferedReader().readText() }
        }
        // Last resort: hardcoded stubs
        return when (kind) {
            Kind.PHP -> PHP_STUB
            Kind.JSON -> JSON_STUB
        }
    }

    companion object {
        private val PHP_STUB = """
            <?php

            return [
                'exp-1_example-experiment' => [
                    'branches' => [
                        'control' => 50,
                        'variant' => 50,
                    ],
                    'start_date' => '2024-01-15',
                ],
                'exp-2_closed-experiment' => [
                    'branches' => [
                        'original' => 80,
                        'test' => 20,
                    ],
                    'override_branch' => 'original',
                ],
            ];
        """.trimIndent()

        private val JSON_STUB = """
            {
                "exp-1_example-experiment": {
                    "branches": [
                        { "value": "control", "percentage": 50 },
                        { "value": "variant", "percentage": 50 }
                    ]
                },
                "exp-2_closed-experiment": {
                    "branches": [
                        { "value": "original", "percentage": 80 },
                        { "value": "test", "percentage": 20 }
                    ],
                    "override_branch": "original"
                }
            }
        """.trimIndent()
    }
}
