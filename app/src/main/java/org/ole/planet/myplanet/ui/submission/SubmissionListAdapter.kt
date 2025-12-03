package org.ole.planet.myplanet.ui.submission

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ItemSubmissionBinding
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.SubmissionPdfGenerator
import org.ole.planet.myplanet.utilities.TimeUtils

class SubmissionListAdapter(
    private val context: Context,
    private val listener: OnHomeItemClickListener?
) : ListAdapter<RealmSubmission, SubmissionListAdapter.ViewHolder>(USER_COMPARATOR) {

    private val mRealm = Realm.getDefaultInstance()

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
                openSubmissionDetail(submission.id)
            }

            binding.btnDownloadPdf.setOnClickListener {
                generateSubmissionPdf(submission)
            }
        }

        private fun openSubmissionDetail(id: String?) {
            val b = Bundle()
            b.putString("id", id)
            val fragment: Fragment = SubmissionDetailFragment()
            fragment.arguments = b
            listener?.openCallFragment(fragment)
        }

        private fun generateSubmissionPdf(submission: RealmSubmission) {
            val file = SubmissionPdfGenerator.generateSubmissionPdf(context, submission, mRealm)

            if (file != null) {
                Toast.makeText(context, "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                openPdf(file)
            } else {
                Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            }
        }

        private fun openPdf(file: java.io.File) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    "Could not open PDF. File saved at: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private val USER_COMPARATOR = object : DiffUtil.ItemCallback<RealmSubmission>() {
            override fun areItemsTheSame(oldItem: RealmSubmission, newItem: RealmSubmission): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: RealmSubmission, newItem: RealmSubmission): Boolean {
                return oldItem.lastUpdateTime == newItem.lastUpdateTime
            }
        }
    }
}
