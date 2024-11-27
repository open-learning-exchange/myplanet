package org.ole.planet.myplanet.utilities

import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.mikepenz.materialdrawer.Drawer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.DialogCampaignChallengeBinding
import org.ole.planet.myplanet.model.RealmUserChallengeActions
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.DashboardElementActivity
import org.ole.planet.myplanet.utilities.Markdown.setMarkdownText

class MarkdownDialog : DialogFragment() {
    private lateinit var dialogCampaignChallengeBinding: DialogCampaignChallengeBinding
    private var markdownContent: String = ""
    private var courseStatus: String = ""
    private var voiceCount: Int = 0
    private var allVoiceCount: Int = 0
    private var hasValidSync: Boolean = false

    companion object {
        private const val ARG_MARKDOWN_CONTENT = "markdown_content"
        private const val ARG_COURSE_STATUS = "course_status"
        private const val ARG_VOICE_COUNT = "voice_count"
        private const val ARG_ALL_VOICE_COUNT = "all_voice_count"
        private const val ARG_HAS_VALID_SYNC = "has_valid_sync"

        fun newInstance(markdownContent: String, courseStatus: String, voiceCount: Int, allVoiceCount: Int, hasValidSync: Boolean): MarkdownDialog {
            val fragment = MarkdownDialog()
            val args = Bundle().apply {
                putString(ARG_MARKDOWN_CONTENT, markdownContent)
                putString(ARG_COURSE_STATUS, courseStatus)
                putInt(ARG_VOICE_COUNT, voiceCount)
                putInt(ARG_ALL_VOICE_COUNT, allVoiceCount)
                putBoolean(ARG_HAS_VALID_SYNC, hasValidSync)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        markdownContent = arguments?.getString(ARG_MARKDOWN_CONTENT) ?: ""
        courseStatus = arguments?.getString(ARG_COURSE_STATUS) ?: ""
        voiceCount = arguments?.getInt(ARG_VOICE_COUNT, 0) ?: 0
        allVoiceCount = arguments?.getInt(ARG_ALL_VOICE_COUNT, 0) ?: 0
        hasValidSync = arguments?.getBoolean(ARG_HAS_VALID_SYNC, false) == true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dialogCampaignChallengeBinding = DialogCampaignChallengeBinding.inflate(inflater, container, false)
        return  dialogCampaignChallengeBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMarkdown()
        setupCourseButton((activity as DashboardActivity).result)
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

        val earnedDollarsVoice = allVoiceCount * 5
        val earnedDollarsCourse = if (courseStatus.contains("terminado")) 5 else 0
        val earnedDollarsSync = if (hasValidSync) 5 else 0
        val total = earnedDollarsVoice + earnedDollarsCourse + earnedDollarsSync
        val progressValue = ((total.toDouble() / 500) * 100).toInt().coerceAtMost(100)
        dialogCampaignChallengeBinding.progressBar.progress = progressValue
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog ?: return

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setupMarkdown() {
        setMarkdownText(dialogCampaignChallengeBinding.markdownTextView, markdownContent)
    }

    private fun setupCourseButton(drawer: Drawer?) {
        dialogCampaignChallengeBinding.btnStart.apply {
            val isCompleted = courseStatus.contains("terminado") && voiceCount >= 5 && (activity as? DashboardActivity)?.mRealm?.let { realm ->
                realm.where(RealmUserChallengeActions::class.java)
                    .equalTo("userId", (activity as? DashboardActivity)?.user?.id)
                    .equalTo("actionType", "sync").count() > 0
            } == true

            visibility = if (isCompleted) View.GONE else View.VISIBLE

            val buttonText = when {
                courseStatus.contains("no iniciado") -> context.getString(R.string.start)
                courseStatus.contains("terminado") && voiceCount < 5 -> context.getString(R.string.next)
                courseStatus.contains("terminado") && voiceCount >= 5 -> context.getString(R.string.sync)
                else -> context.getString(R.string.continuation)
            }

            text = buttonText
            setOnClickListener {
                val courseId = "9517e3b45a5bb63e69bb8f269216974d"
                when (buttonText) {
                    context.getString(R.string.start), context.getString(R.string.continuation) -> {
                        val fragment = TakeCourseFragment().apply {
                            arguments = Bundle().apply {
                                putString("id", courseId)
                            }
                        }
                        (activity as? OnHomeItemClickListener)?.openCallFragment(fragment)
                    }
                    context.getString(R.string.next) -> {
                        (activity as DashboardActivity).openCallFragment(CommunityTabFragment())
                    }
                    context.getString(R.string.sync) -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            (activity as DashboardElementActivity).logSyncInSharedPrefs()
                        }
                    }
                }
                if (drawer != null && drawer.isDrawerOpen) {
                    drawer.closeDrawer()
                }
                dismiss()
            }
        }
    }

    private fun setupCloseButton() {
        dialogCampaignChallengeBinding.closeButton.setOnClickListener {
            dismiss()
        }
    }
}
