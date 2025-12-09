package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ItemSubmissionBinding
import org.ole.planet.myplanet.utilities.TimeUtils

class SubmissionListAdapter(
    private val context: Context,
    private val listener: OnHomeItemClickListener?,
    private val onGeneratePdf: (String?) -> Unit
) : ListAdapter<SubmissionItem, SubmissionListAdapter.ViewHolder>(USER_COMPARATOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubmissionBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val submission = getItem(position)
        holder.bind(submission, position + 1)
    }

    inner class ViewHolder(private val binding: ItemSubmissionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(submission: SubmissionItem, number: Int) {
            binding.tvSubmissionNumber.text = "#$number"
            binding.tvSubmissionDate.text = TimeUtils.getFormattedDateWithTime(submission.submission.lastUpdateTime)
            binding.tvSubmissionStatus.text = submission.submission.status

            binding.tvSyncStatus.text = if (submission.submission.uploaded) "✅" else "❌"

            binding.btnViewDetails.setOnClickListener {
                openSubmissionDetail(submission.submission.id)
            }

            binding.btnDownloadPdf.setOnClickListener {
                onGeneratePdf(submission.submission.id)
            }
        }

        private fun openSubmissionDetail(id: String?) {
            val b = Bundle()
            b.putString("id", id)
            val fragment: Fragment = SubmissionDetailFragment()
            fragment.arguments = b
            listener?.openCallFragment(fragment)
        }
    }

    companion object {
        private val USER_COMPARATOR = object : DiffUtil.ItemCallback<SubmissionItem>() {
            override fun areItemsTheSame(oldItem: SubmissionItem, newItem: SubmissionItem): Boolean {
                return oldItem.submission.id == newItem.submission.id
            }

            override fun areContentsTheSame(oldItem: SubmissionItem, newItem: SubmissionItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
