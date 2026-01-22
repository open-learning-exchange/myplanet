package org.ole.planet.myplanet.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class HardcodedCodeDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "HardcodedTextCode",
            briefDescription = "Hardcoded text",
            explanation = "Hardcoded text should be avoided in code. Use string resources instead.",
            category = Category.I18N,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                HardcodedCodeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("setText")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, "android.widget.TextView")) {
            val args = node.valueArguments
            if (args.isNotEmpty()) {
                checkArgument(context, args[0])
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UBinaryExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitBinaryExpression(node: UBinaryExpression) {
                if (node.operator.text == "=") {
                    val left = node.leftOperand
                    // Check for .text = "..."
                    if (left is UQualifiedReferenceExpression) {
                        val selector = left.selector
                         if (selector is USimpleNameReferenceExpression && selector.identifier == "text") {
                             checkArgument(context, node.rightOperand)
                         }
                    }
                }
            }
        }
    }

    private fun checkArgument(context: JavaContext, node: UExpression) {
        if (node is ULiteralExpression) {
            val value = node.value
            if (value is String && value.isNotEmpty()) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Hardcoded string \"$value\", should use @string resource"
                )
            }
        } else if (node is UPolyadicExpression) {
             // String concatenation or interpolation
             val hasStringLiteral = node.operands.any {
                 it is ULiteralExpression && it.value is String && (it.value as String).isNotEmpty()
             }
             if (hasStringLiteral) {
                 context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Hardcoded string concatenation/template, should use @string resource"
                 )
             }
        }
    }
}
