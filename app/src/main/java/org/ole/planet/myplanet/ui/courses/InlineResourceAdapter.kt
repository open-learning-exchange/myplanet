package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemInlineResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.ResourceOpener
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class InlineResourceAdapter(
    private val onResourceClick: (RealmMyLibrary) -> Unit
) : ListAdapter<RealmMyLibrary, InlineResourceAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<RealmMyLibrary>() {
        override fun areItemsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: RealmMyLibrary, newItem: RealmMyLibrary): Boolean {
            return oldItem._rev == newItem._rev
        }
    }

    class ViewHolder(val binding: ItemInlineResourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInlineResourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resource = getItem(position)
        val context = holder.itemView.context
        val binding = holder.binding

        binding.tvResourceTitle.text = resource.title ?: resource.resourceLocalAddress ?: ""

        val isDownloaded = resource.isResourceOffline() ||
            FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))

        val mimeType = Utilities.getMimeType(resource.resourceLocalAddress)

        binding.ivResourcePreview.visibility = View.GONE
        binding.videoThumbnailContainer.visibility = View.GONE
        binding.tvTextPreview.visibility = View.GONE
        binding.audioPreviewContainer.visibility = View.GONE
        binding.pbDownload.visibility = View.GONE
        binding.ivStatus.visibility = View.GONE

        if (isDownloaded) {
            binding.ivStatus.visibility = View.VISIBLE
            binding.ivStatus.setImageResource(R.drawable.ic_eye)

            val resourceFile = File(
                context.getExternalFilesDir(null),
                "ole/${resource.id}/${resource.resourceLocalAddress}"
            )

            when {
                mimeType?.startsWith("image") == true -> {
                    showImagePreview(binding, context, resourceFile)
                }
                mimeType?.startsWith("video") == true -> {
                    showVideoPreview(binding, context, resourceFile)
                }
                mimeType?.contains("pdf") == true -> {
                    showPdfPreview(binding, context, resourceFile)
                }
                mimeType?.startsWith("audio") == true -> {
                    showAudioPreview(binding, resourceFile)
                }
                mimeType?.contains("csv") == true || resource.resourceLocalAddress?.endsWith(".csv") == true -> {
                    showCsvPreview(binding, resourceFile)
                }
                mimeType?.startsWith("text") == true || resource.resourceLocalAddress?.endsWith(".txt") == true || resource.resourceLocalAddress?.endsWith(".md") == true -> {
                    showTextPreview(binding, resourceFile)
                }
            }
        } else {
            binding.pbDownload.visibility = View.VISIBLE
        }

        binding.ivResourceIcon.setImageResource(
            ResourceOpener.getResourceTypeIcon(resource.resourceLocalAddress)
        )

        binding.cardResource.setOnClickListener {
            onResourceClick(resource)
        }
    }

    private fun showImagePreview(binding: ItemInlineResourceBinding, context: Context, file: File) {
        if (file.exists()) {
            binding.ivResourcePreview.visibility = View.VISIBLE
            Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.ole_logo)
                .error(R.drawable.ole_logo)
                .into(binding.ivResourcePreview)
        }
    }

    private fun showVideoPreview(binding: ItemInlineResourceBinding, context: Context, file: File) {
        binding.videoThumbnailContainer.visibility = View.VISIBLE
        if (file.exists()) {
            Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(binding.ivVideoThumbnail)
        }
    }

    private fun showPdfPreview(binding: ItemInlineResourceBinding, context: Context, file: File) {
        if (!file.exists()) return
        try {
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val page = pdfRenderer.openPage(0)
            val scale = 2
            val bitmap = createBitmap(page.width * scale, page.height * scale)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pdfRenderer.close()
            fileDescriptor.close()

            binding.ivResourcePreview.visibility = View.VISIBLE
            binding.ivResourcePreview.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            binding.ivResourcePreview.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            binding.ivResourcePreview.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAudioPreview(binding: ItemInlineResourceBinding, file: File) {
        binding.audioPreviewContainer.visibility = View.VISIBLE
        if (!file.exists()) return
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()

            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            binding.tvAudioDuration.text = String.format("%d:%02d", minutes, seconds)
        } catch (e: Exception) {
            binding.tvAudioDuration.text = ""
        }
    }

    private fun showCsvPreview(binding: ItemInlineResourceBinding, file: File) {
        if (!file.exists()) return
        try {
            val reader = CSVReaderBuilder(FileReader(file))
                .withCSVParser(CSVParserBuilder().withSeparator(',').withQuoteChar('"').build())
                .build()
            val preview = StringBuilder()
            reader.use { csvReader ->
                var rowCount = 0
                for (row in csvReader) {
                    if (rowCount >= 5) break
                    preview.appendLine(row.joinToString("  |  "))
                    rowCount++
                }
            }
            if (preview.isNotEmpty()) {
                binding.tvTextPreview.visibility = View.VISIBLE
                binding.tvTextPreview.text = preview.toString().trimEnd()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showTextPreview(binding: ItemInlineResourceBinding, file: File) {
        if (!file.exists()) return
        try {
            val lines = file.bufferedReader().useLines { it.take(8).toList() }
            if (lines.isNotEmpty()) {
                binding.tvTextPreview.visibility = View.VISIBLE
                binding.tvTextPreview.text = lines.joinToString("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateResources(newResources: List<RealmMyLibrary>) {
        submitList(newResources)
    }
}
