package org.ole.planet.myplanet.utilities

import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import org.ole.planet.myplanet.databinding.DialogCampaignChallengeBinding
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class MarkdownDialog : DialogFragment() {
    private lateinit var dialogCampaignChallengeBinding: DialogCampaignChallengeBinding
    private var markdownContent: String = ""

    companion object {
        private const val ARG_MARKDOWN_CONTENT = "markdown_content"

        fun newInstance(markdownContent: String): MarkdownDialog {
            val fragment = MarkdownDialog()
            val args = Bundle().apply {
                putString(ARG_MARKDOWN_CONTENT, markdownContent)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        markdownContent = arguments?.getString(ARG_MARKDOWN_CONTENT) ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dialogCampaignChallengeBinding = DialogCampaignChallengeBinding.inflate(inflater, container, false)
        return  dialogCampaignChallengeBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMarkdown()
        dialogCampaignChallengeBinding.markdownTextView.movementMethod = LinkMovementMethod.getInstance()
        val textWithSpans = dialogCampaignChallengeBinding.markdownTextView.text
        if (textWithSpans is Spannable) {
            val urlSpans = textWithSpans.getSpans(0, textWithSpans.length, URLSpan::class.java)
            for (urlSpan in urlSpans) {
                val start = textWithSpans.getSpanStart(urlSpan)
                val end = textWithSpans.getSpanEnd(urlSpan)
                val dynamicTitle = textWithSpans.subSequence(start, end).toString()
                textWithSpans.setSpan(CustomClickableSpan(urlSpan.url, dynamicTitle, requireActivity()), start, end, textWithSpans.getSpanFlags(urlSpan))
                textWithSpans.removeSpan(urlSpan)
            }
        }
        setupCloseButton()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog ?: return

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setupMarkdown() {
        setMarkdownText(dialogCampaignChallengeBinding.markdownTextView, markdownContent)
    }

    private fun setupCloseButton() {
        dialogCampaignChallengeBinding.closeButton.setOnClickListener {
            dismiss()
        }
    }
}
