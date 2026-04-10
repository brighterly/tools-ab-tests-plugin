package com.brighterly.experiments.util

import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression

/**
 * Returns the experiment key (e.g. "exp-23_foo") if this element is a string
 * literal containing one, for both PHP StringLiteralExpression and JS JSLiteralExpression.
 * Returns null otherwise.
 */
fun PsiElement.experimentKey(): String? = when (this) {
    is StringLiteralExpression -> contents.takeIf { it.startsWith("exp-") }
    is JSLiteralExpression -> if (isStringLiteral) stringValue?.takeIf { it.startsWith("exp-") } else null
    else -> null
}

/**
 * Returns the TextRange within the element that covers just the key text (inside quotes).
 * Used when attaching references: offset 1 skips the opening quote.
 */
fun PsiElement.experimentKeyTextRange(): TextRange? {
    val key = experimentKey() ?: return null
    return TextRange(1, key.length + 1)
}
