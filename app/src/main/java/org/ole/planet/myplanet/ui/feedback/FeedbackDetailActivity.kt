package org.ole.planet.myplanet.ui.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityFeedbackDetailBinding
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.LocaleUtils
import org.ole.planet.myplanet.utils.TimeUtils.getFormattedDateWithTime
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class FeedbackDetailActivity : AppCompatActivity() {
    private lateinit var activityFeedbackDetailBinding: ActivityFeedbackDetailBinding
    private var replyAdapter: FeedbackReplyAdapter? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var feedback: Feedback? = null
    private lateinit var feedbackId: String
    private val viewModel: FeedbackDetailViewModel by viewModels()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityFeedbackDetailBinding = ActivityFeedbackDetailBinding.inflate(layoutInflater)
        setContentView(activityFeedbackDetailBinding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, activityFeedbackDetailBinding.root)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.feedback)
        val id = intent.getStringExtra("id")
        if (id.isNullOrEmpty()) {
            finish()
            return
        }
        feedbackId = id
        setUpReplies()

        collectLatestWhenStarted(viewModel.feedback) { fb ->
            fb?.let {
                feedback = it
                activityFeedbackDetailBinding.tvDate.text = getFormattedDateWithTime(it.openTime)
                val message = it.message
                activityFeedbackDetailBinding.tvMessage.text =
                    if (TextUtils.isEmpty(message)) "N/A" else message
                replyAdapter = FeedbackReplyAdapter(this@FeedbackDetailActivity)
                activityFeedbackDetailBinding.rvFeedbackReply.adapter = replyAdapter
                replyAdapter?.submitList(it.messageList)
                updateForClosed()
            }
        }

        collectLatestWhenStarted(viewModel.events) { event ->
            when (event) {
                is FeedbackDetailViewModel.FeedbackDetailEvent.CloseFeedbackSuccess ->
                    navigateToFeedbackListFragment()
            }
        }

        activityFeedbackDetailBinding.closeFeedback.setOnClickListener {
            viewModel.closeFeedback(feedbackId)
        }
        activityFeedbackDetailBinding.replyFeedback.setOnClickListener {
            if (TextUtils.isEmpty(activityFeedbackDetailBinding.feedbackReplyEditText.text.toString().trim { it <= ' ' })) {
                activityFeedbackDetailBinding.feedbackReplyEditText.error =
                    getString(R.string.kindly_enter_reply_message)
            } else {
                val message = activityFeedbackDetailBinding.feedbackReplyEditText.text.toString().trim { it <= ' ' }
                viewModel.addReply(feedbackId, message, feedback?.owner)
                activityFeedbackDetailBinding.feedbackReplyEditText.setText(R.string.empty_text)
                activityFeedbackDetailBinding.feedbackReplyEditText.clearFocus()
            }
        }

        viewModel.loadFeedback(feedbackId)
    }

    private fun setUpReplies() {
        layoutManager = LinearLayoutManager(this)
        activityFeedbackDetailBinding.rvFeedbackReply.layoutManager = layoutManager
    }

    private fun updateForClosed() {
        if (feedback?.status.equals("Closed", ignoreCase = true)) {
            activityFeedbackDetailBinding.closeFeedback.isEnabled = false
            activityFeedbackDetailBinding.replyFeedback.isEnabled = false
            activityFeedbackDetailBinding.feedbackReplyEditText.visibility = View.INVISIBLE
        }
    }

    private fun navigateToFeedbackListFragment() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("fragmentToOpen", "feedbackList")
        startActivity(intent)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
