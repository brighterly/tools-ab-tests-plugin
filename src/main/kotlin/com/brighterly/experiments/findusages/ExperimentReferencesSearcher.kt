package com.brighterly.experiments.findusages

import com.brighterly.experiments.reference.ExperimentKeyReference
import com.brighterly.experiments.service.ExperimentsService
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

class ExperimentReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(
        parameters: ReferencesSearch.SearchParameters,
        consumer: Processor<in PsiReference>,
    ) {
        val element = parameters.elementToSearch as? StringLiteralExpression ?: return
        val key = element.contents
        if (!key.startsWith("exp-")) return

        val configPath = element.containingFile?.virtualFile?.path ?: return
        if (!ExperimentsService.getInstance().isConfigFile(configPath)) return

        val project = parameters.project
        val globalScope = parameters.effectiveSearchScope as? GlobalSearchScope
            ?: GlobalSearchScope.projectScope(project)
        val manager = PsiManager.getInstance(project)

        // Scan PHP files
        FileTypeIndex.getFiles(PhpFileType.INSTANCE, globalScope).forEach { vFile ->
            if (vFile.path == configPath) return@forEach
            val psiFile = manager.findFile(vFile) ?: return@forEach
            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(visited: PsiElement) {
                    if (visited is StringLiteralExpression && visited.contents == key) {
                        for (ref in visited.references) {
                            if (ref is ExperimentKeyReference) consumer.process(ref)
                        }
                    }
                    super.visitElement(visited)
                }
            })
        }

        // Scan JavaScript and TypeScript files
        val ftManager = FileTypeManager.getInstance()
        for (ext in listOf("js", "ts", "jsx", "tsx")) {
            val fileType = ftManager.getFileTypeByExtension(ext)
            if (fileType is UnknownFileType) continue
            FileTypeIndex.getFiles(fileType, globalScope).forEach { vFile ->
                val psiFile = manager.findFile(vFile) ?: return@forEach
                psiFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(visited: PsiElement) {
                        if (visited is JSLiteralExpression && visited.isStringLiteral && visited.stringValue == key) {
                            for (ref in visited.references) {
                                if (ref is ExperimentKeyReference) consumer.process(ref)
                            }
                        }
                        super.visitElement(visited)
                    }
                })
            }
        }
    }
}
