package org.ole.planet.myplanet.ui.submission

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ItemSubmissionBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.utilities.TimeUtils

class SubmissionListAdapter(
    private val context: Context,
    private val submissions: List<RealmSubmission>,
    private val databaseService: DatabaseService,
    private val listener: OnHomeItemClickListener?,
    private val pdfGeneratorViewModel: PdfGeneratorViewModel
) : RecyclerView.Adapter<SubmissionListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubmissionBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val submission = submissions[position]
        holder.bind(submission, position + 1)
    }

    override fun getItemCount(): Int = submissions.size

    inner class ViewHolder(private val binding: ItemSubmissionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val progressDialog = ProgressDialog(context).apply {
            setMessage("Generating PDF...")
            setCancelable(true)
            setOnCancelListener {
                pdfGeneratorViewModel.cancel()
            }
        }

        fun bind(submission: RealmSubmission, number: Int) {
            binding.tvSubmissionNumber.text = "#$number"
            binding.tvSubmissionDate.text =
                TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)
            binding.tvSubmissionStatus.text = submission.status

            binding.tvSyncStatus.text = if (submission.uploaded) "✅" else "❌"

            binding.btnViewDetails.setOnClickListener {
                openSubmissionDetail(submission.id)
            }

            binding.btnDownloadPdf.setOnClickListener {
                generateSubmissionPdf(submission)
            }

            pdfGeneratorViewModel.pdfGenerationState.observe(context as LifecycleOwner) { state ->
                when (state) {
                    is PdfGenerationState.Loading -> progressDialog.show()
                    is PdfGenerationState.Success -> {
                        progressDialog.dismiss()
                        Toast.makeText(
                            context,
                            "PDF saved to ${state.file.absolutePath}",
                            Toast.LENGTH_LONG
                        )
                            .show()
                        openPdf(state.file)
                    }

                    is PdfGenerationState.Error -> {
                        progressDialog.dismiss()
                        Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                    }

                    else -> progressDialog.dismiss()
                }
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
            pdfGeneratorViewModel.generatePdf(context, submission)
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
}
