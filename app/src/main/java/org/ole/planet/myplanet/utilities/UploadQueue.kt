package org.ole.planet.myplanet.utilities

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class UploadQueue(private val context: Context) {
    private val uploadJobs = ConcurrentLinkedQueue<UploadJob>()
    private var isProcessing = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var progressDialog: AlertDialog? = null

    // Interface for upload completion callback
    interface UploadCompletionListener {
        fun onUploadComplete(success: Boolean, message: String)
    }

    // Define an upload job
    data class UploadJob(
        val name: String,
        val action: () -> Unit,
        val priority: Int = 0
    )

    // Add a job to the queue
    fun enqueue(name: String, priority: Int = 0, action: () -> Unit) {
        uploadJobs.add(UploadJob(name, action, priority))
        if (!isProcessing.get()) {
            startProcessing()
        }
    }

    // Process all queued uploads sequentially
    private fun startProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            showProgressDialog()

            MainApplication.applicationScope.launch(Dispatchers.IO) {
                try {
                    // Sort by priority (higher number = higher priority)
                    val sortedJobs = uploadJobs.sortedByDescending { it.priority }
                    uploadJobs.clear()
                    uploadJobs.addAll(sortedJobs)

                    while (uploadJobs.isNotEmpty()) {
                        val job = uploadJobs.poll()
                        if (job != null) {
                            updateProgress("Uploading ${job.name}...")
                        }

                        try {
                            if (job != null) {
                                job.action()
                            }
                        } catch (e: Exception) {
                            if (job != null) {
                                Log.e("UploadQueue", "Error in job ${job.name}", e)
                            }
                            // Continue with next job even if this one fails
                        }
                    }

                    withContext(Dispatchers.Main) {
                        dismissProgressDialog()
                        Toast.makeText(context, "All uploads completed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("UploadQueue", "Error in upload queue", e)
                    withContext(Dispatchers.Main) {
                        dismissProgressDialog()
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isProcessing.set(false)
                }
            }
        }
    }

    // Update the progress dialog message
    private fun updateProgress(message: String) {
        handler.post {
            // Update dialog message
            progressDialog?.setMessage(message)
        }
    }

    // Show progress dialog
    private fun showProgressDialog() {
        handler.post {
            progressDialog = AlertDialog.Builder(context)
                .setTitle("Uploading Data")
                .setMessage("Preparing uploads...")
                .setCancelable(false)
                .create()
            progressDialog?.show()
        }
    }

    // Dismiss progress dialog
    private fun dismissProgressDialog() {
        handler.post {
            progressDialog?.dismiss()
            progressDialog = null
        }
    }

    // Cancel all pending uploads
    fun cancelAll() {
        uploadJobs.clear()
        // Note: This doesn't stop the currently executing job
    }

    companion object {
        @Volatile
        var instance: UploadQueue? = null

        fun getInstance(context: Context): UploadQueue {
            return instance ?: synchronized(this) {
                instance ?: UploadQueue(context).also { instance = it }
            }
        }
    }
}