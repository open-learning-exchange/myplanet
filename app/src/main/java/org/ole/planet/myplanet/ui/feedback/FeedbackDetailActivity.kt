package org.ole.planet.myplanet.ui.feedback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityFeedbackDetailBinding
import org.ole.planet.myplanet.databinding.RowFeedbackReplyBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.FeedbackReply
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.feedback.FeedbackDetailActivity.RvFeedbackAdapter.ReplyViewHolder
import org.ole.planet.myplanet.utilities.TimeUtils.getFormatedDateWithTime
import java.util.Date

class FeedbackDetailActivity : AppCompatActivity() {
    private lateinit var activityFeedbackDetailBinding: ActivityFeedbackDetailBinding
    private var mAdapter: RecyclerView.Adapter<*>? = null
    private var layoutManager: RecyclerView.LayoutManager? = null
    var feedback: RealmFeedback? = null
    var realm: Realm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityFeedbackDetailBinding = ActivityFeedbackDetailBinding.inflate(layoutInflater)
        setContentView(activityFeedbackDetailBinding.root)
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setTitle(R.string.feedback)
        realm = DatabaseService(this).realmInstance
        feedback = realm!!.where(RealmFeedback::class.java).equalTo("id", intent.getStringExtra("id")).findFirst()
        if (!TextUtils.isEmpty(feedback!!.openTime))
            activityFeedbackDetailBinding.tvDate.text = getFormatedDateWithTime(feedback!!.openTime!!.toLong())
        else
            activityFeedbackDetailBinding.tvDate.setText(R.string.date_n_a)
        activityFeedbackDetailBinding.tvMessage.text = if (TextUtils.isEmpty(feedback!!.message))
            "N/A"
        else
            feedback!!.message
        setUpReplies()
    }

    private fun setUpReplies() {
        activityFeedbackDetailBinding.rvFeedbackReply.setHasFixedSize(true)
        layoutManager = LinearLayoutManager(this)
        activityFeedbackDetailBinding.rvFeedbackReply.layoutManager = layoutManager
        mAdapter = RvFeedbackAdapter(feedback!!.messageList, applicationContext)
        activityFeedbackDetailBinding.rvFeedbackReply.adapter = mAdapter
        activityFeedbackDetailBinding.closeFeedback.setOnClickListener {
            realm!!.executeTransactionAsync(Realm.Transaction { realm1: Realm ->
                val feedback1 = realm1.where(RealmFeedback::class.java)
                    .equalTo("id", intent.getStringExtra("id")).findFirst()
                feedback1!!.status = "Closed" },
                Realm.Transaction.OnSuccess { updateForClosed() })
        }
        activityFeedbackDetailBinding.replyFeedback.setOnClickListener {
            if (TextUtils.isEmpty(activityFeedbackDetailBinding.feedbackReplyEditText.text.toString().trim { it <= ' ' })) {
                activityFeedbackDetailBinding.feedbackReplyEditText.error = "Kindly enter reply message"
            } else {
                val message = activityFeedbackDetailBinding.feedbackReplyEditText.text.toString().trim { it <= ' ' }
                val `object` = JsonObject()
                `object`.addProperty("message", message)
                `object`.addProperty("time", Date().time.toString() + "")
                `object`.addProperty("user", feedback!!.owner + "")
                val id = feedback!!.id
                addReply(`object`, id)
                mAdapter = RvFeedbackAdapter(feedback!!.messageList, applicationContext)
                activityFeedbackDetailBinding.rvFeedbackReply.adapter = mAdapter
                activityFeedbackDetailBinding.feedbackReplyEditText.setText("")
                activityFeedbackDetailBinding.feedbackReplyEditText.clearFocus()
            }
        }
    }

    private fun updateForClosed() {
        if (feedback!!.status.equals("Closed", ignoreCase = true)) {
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

    private fun addReply(obj: JsonObject?, id: String?) {
        realm!!.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val feedback = realm.where(RealmFeedback::class.java).equalTo("id", id).findFirst()
            if (feedback != null) {
                val con = Gson()
                val msgArray = con.fromJson(feedback.messages, JsonArray::class.java)
                Log.e("Msg", Gson().toJson(msgArray))
                msgArray.add(obj)
                feedback.setMessages(msgArray)
            }
        }, Realm.Transaction.OnSuccess {
            updateForClosed()
            mAdapter = RvFeedbackAdapter(feedback!!.messageList, applicationContext)
            activityFeedbackDetailBinding.rvFeedbackReply.adapter = mAdapter
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    inner class RvFeedbackAdapter(private val replyList: List<FeedbackReply>?, var context: Context) : RecyclerView.Adapter<ReplyViewHolder>() {
        private var rowFeedbackReplyBinding: RowFeedbackReplyBinding? = null
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReplyViewHolder {
            rowFeedbackReplyBinding = RowFeedbackReplyBinding.inflate(
                LayoutInflater.from(context), parent, false
            )
            return ReplyViewHolder(rowFeedbackReplyBinding!!)
        }

        override fun onBindViewHolder(holder: ReplyViewHolder, position: Int) {
            rowFeedbackReplyBinding!!.tvDate.text = getFormatedDateWithTime(replyList!![position].date.toLong())
            rowFeedbackReplyBinding!!.tvUser.text = replyList[position].user
            rowFeedbackReplyBinding!!.tvMessage.text = replyList[position].message
        }

        override fun getItemCount(): Int {
            return replyList!!.size
        }

        inner class ReplyViewHolder(rowFeedbackReplyBinding: RowFeedbackReplyBinding) : RecyclerView.ViewHolder(rowFeedbackReplyBinding.root)
    }
}
