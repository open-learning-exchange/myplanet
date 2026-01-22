package org.ole.planet.myplanet.utils

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import org.ole.planet.myplanet.R

fun TextView.makeExpandable(
    fullText: CharSequence,
    collapsedMaxLines: Int = 6,
    expandLabel: String = context.getString(R.string.show_more),
    collapseLabel: String = context.getString(R.string.show_less)
) {
    var isExpanded = false

    fun refresh() {
        text = fullText
        if (!isExpanded) {
            maxLines = collapsedMaxLines
            ellipsize = TextUtils.TruncateAt.END

            post {
                if (lineCount > collapsedMaxLines) {
                    val lastChar = layout.getLineEnd(collapsedMaxLines - 2)
                    val safeLastChar = minOf(lastChar, fullText.length)

                    if (safeLastChar > 0 && safeLastChar <= fullText.length) {
                        val visiblePortion = SpannableStringBuilder(fullText.subSequence(0, safeLastChar))
                        visiblePortion.append("… ").also { sb ->
                            val start = sb.length
                            sb.append(expandLabel)
                            sb.setSpan(object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    isExpanded = true
                                    refresh()
                                }
                            }, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }

                        text = visiblePortion
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                }
            }
        } else {
            val expanded = SpannableStringBuilder(fullText).apply {
                append(" ")
                val start = length
                append(collapseLabel)
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        isExpanded = false
                        refresh()
                    }
                }, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            text = expanded
            movementMethod = LinkMovementMethod.getInstance()
            maxLines = Int.MAX_VALUE
            ellipsize = null
        }
    }
    refresh()
}
