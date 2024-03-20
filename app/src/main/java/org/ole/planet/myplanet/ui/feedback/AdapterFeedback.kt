package org.ole.planet.myplanet.ui.feedback

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import io.realm.RealmResults
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowFeedbackBinding
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.ui.feedback.AdapterFeedback.ViewHolderFeedback
import org.ole.planet.myplanet.utilities.TimeUtils.getFormatedDate

class AdapterFeedback(private val context: Context, private var list: List<RealmFeedback>?) : RecyclerView.Adapter<ViewHolderFeedback>() {
    private lateinit var rowFeedbackBinding: RowFeedbackBinding

    fun updateData(newData: RealmResults<RealmFeedback>) {
        list = newData
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderFeedback {
        rowFeedbackBinding = RowFeedbackBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolderFeedback(rowFeedbackBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderFeedback, position: Int) {
        rowFeedbackBinding.tvTitle.text = list?.get(position)?.title
        rowFeedbackBinding.tvType.text = list?.get(position)?.type
        rowFeedbackBinding.tvPriority.text = list?.get(position)?.priority
        rowFeedbackBinding.tvStatus.text = list?.get(position)?.status
        val contentDescription = "${list?.get(position)?.title}, ${list?.get(position)?.type}, " +
                "${context.getString(R.string.status)}: ${list?.get(position)?.status}, ${context.getString(R.string.priority)}: ${list?.get(position)?.priority}, " +
                "${context.getString(R.string.open_date)}: ${list?.get(position)?.openTime?.toLong()?.let { getFormatedDate(it) }}"
        rowFeedbackBinding.feedbackCardView.contentDescription = contentDescription

        if ("yes".equals(list?.get(position)?.priority, ignoreCase = true)) {
            rowFeedbackBinding.tvPriority.background = ResourcesCompat.getDrawable(context.resources, R.drawable.bg_primary, null)
        } else {
            rowFeedbackBinding.tvPriority.background = ResourcesCompat.getDrawable(context.resources, R.drawable.bg_grey, null)
        }
        rowFeedbackBinding.tvStatus.background = ResourcesCompat.getDrawable(context.resources,
            if ("open".equals(list?.get(position)?.status, ignoreCase = true)) {
                R.drawable.bg_primary
            } else {
                R.drawable.bg_grey
            }, null)
        rowFeedbackBinding.tvOpenDate.text = getFormatedDate(list?.get(position)?.openTime?.toLong())
        rowFeedbackBinding.root.setOnClickListener {
            context.startActivity(Intent(context, FeedbackDetailActivity::class.java)
                .putExtra("id", list?.get(position)?.id))
        }
    }

    override fun getItemCount(): Int {
        return list?.size ?: 0
    }

    class ViewHolderFeedback(rowFeedbackBinding: RowFeedbackBinding) : RecyclerView.ViewHolder(rowFeedbackBinding.root)
}
