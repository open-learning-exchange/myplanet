package org.ole.planet.myplanet.ui.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityFeedbackDetailBinding
import org.ole.planet.myplanet.databinding.RowFeedbackReplyBinding
import org.ole.planet.myplanet.model.FeedbackReply
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.feedback.FeedbackDetailActivity.RvFeedbackAdapter.ReplyViewHolder
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDateWithTime

@AndroidEntryPoint
class FeedbackDetailActivity : AppCompatActivity() {
    private lateinit var activityFeedbackDetailBinding: ActivityFeedbackDetailBinding
    private var mAdapter: RecyclerView.Adapter<*>? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    private var feedback: RealmFeedback? = null
    private lateinit var rowFeedbackReplyBinding: RowFeedbackReplyBinding
    private lateinit var feedbackId: String
    private val viewModel: FeedbackDetailViewModel by viewModels()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
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

        lifecycleScope.launch {
            viewModel.feedback.collectLatest { fb ->
                fb?.let {
                    feedback = it
                    activityFeedbackDetailBinding.tvDate.text = getFormattedDateWithTime(it.openTime)
                    activityFeedbackDetailBinding.tvMessage.text =
                        if (TextUtils.isEmpty(it.message)) "N/A" else it.message
                    mAdapter = RvFeedbackAdapter(it.messageList, applicationContext)
                    activityFeedbackDetailBinding.rvFeedbackReply.adapter = mAdapter
                    updateForClosed()
                }
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
                val obj = JsonObject().apply {
                    addProperty("message", message)
                    addProperty("time", Date().time.toString())
                    addProperty("user", feedback?.owner ?: "")
                }
                viewModel.addReply(feedbackId, obj)
                activityFeedbackDetailBinding.feedbackReplyEditText.setText(R.string.empty_text)
                activityFeedbackDetailBinding.feedbackReplyEditText.clearFocus()
            }
        }

        viewModel.loadFeedback(feedbackId)
    }

    private fun setUpReplies() {
        activityFeedbackDetailBinding.rvFeedbackReply.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(this)
        activityFeedbackDetailBinding.rvFeedbackReply.layoutManager = layoutManager
    }

    private fun updateForClosed() {
        if (feedback?.statusEnum?.isClosed == true) {
            activityFeedbackDetailBinding.closeFeedback.isEnabled = false
            activityFeedbackDetailBinding.replyFeedback.isEnabled = false
            activityFeedbackDetailBinding.feedbackReplyEditText.visibility = View.INVISIBLE
            navigateToFeedbackListFragment()
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

    inner class RvFeedbackAdapter(private val replyList: List<FeedbackReply>?, var context: Context) : RecyclerView.Adapter<ReplyViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
            rowFeedbackReplyBinding = RowFeedbackReplyBinding.inflate(layoutInflater, parent, false)
            return ReplyViewHolder(rowFeedbackReplyBinding)
        }

        override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
            rowFeedbackReplyBinding.tvDate.text = replyList?.get(position)?.date?.let {
                getFormattedDateWithTime(it.toLong())
            }
            rowFeedbackReplyBinding.tvUser.text = replyList?.get(position)?.user
            rowFeedbackReplyBinding.tvMessage.text = replyList?.get(position)?.message
        }

        override fun getItemCount(): Int {
            return replyList?.size ?: 0
        }

        inner class ReplyViewHolder(rowFeedbackReplyBinding: RowFeedbackReplyBinding) : RecyclerView.ViewHolder(rowFeedbackReplyBinding.root)
    }
}
