package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.ItemSubmissionBinding
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.TimeUtils

class SubmissionListAdapter(
    private val context: Context,
    private val onAction: (RealmSubmission, String) -> Unit
) : ListAdapter<RealmSubmission, SubmissionListAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubmissionBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val submission = getItem(position)
        holder.bind(submission, position + 1)
    }

    inner class ViewHolder(private val binding: ItemSubmissionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(submission: RealmSubmission, number: Int) {
            binding.tvSubmissionNumber.text = "#$number"
            binding.tvSubmissionDate.text = TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)
            binding.tvSubmissionStatus.text = submission.status
            binding.tvSyncStatus.text = if (submission.uploaded) "✅" else "❌"

            binding.btnViewDetails.setOnClickListener {
                onAction(submission, "view")
            }

            binding.btnDownloadPdf.setOnClickListener {
                onAction(submission, "download")
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RealmSubmission>() {
            override fun areItemsTheSame(oldItem: RealmSubmission, newItem: RealmSubmission): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RealmSubmission, newItem: RealmSubmission): Boolean {
                return oldItem.status == newItem.status && oldItem.lastUpdateTime == newItem.lastUpdateTime
            }
        }
    }
}
