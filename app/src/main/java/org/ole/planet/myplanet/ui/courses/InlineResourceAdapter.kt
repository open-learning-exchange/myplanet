package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.File
import java.io.FileReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemInlineResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.ResourceOpener
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class InlineResourceAdapter(
    private val parentScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val onResourceClick: (RealmMyLibrary) -> Unit
) : ListAdapter<RealmMyLibrary, InlineResourceAdapter.ViewHolder>(
    DiffUtils.itemCallback<RealmMyLibrary>(
        areItemsTheSame = { old, new -> old.id == new.id },
        areContentsTheSame = { old, new ->
            old.resourceLocalAddress == new.resourceLocalAddress &&
                old.title == new.title &&
                old.isResourceOffline() == new.isResourceOffline()
        },
        getChangePayload = { old, new ->
            val payloads = mutableListOf<String>()
            if (old.title != new.title) payloads.add("TITLE")
            if (old.resourceLocalAddress != new.resourceLocalAddress) payloads.add("ADDRESS")
            if (old.isResourceOffline() != new.isResourceOffline()) payloads.add("STATUS")
            if (payloads.isEmpty()) null else payloads
        }
    )
) {

    private var externalFilesDir: java.io.File? = null
    private val textCache = mutableMapOf<String, String>()
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val bitmapCache = object : LruCache<String, android.graphics.Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: android.graphics.Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    class ViewHolder(val binding: ItemInlineResourceBinding, private val parentScope: CoroutineScope, private val dispatcherProvider: DispatcherProvider) : RecyclerView.ViewHolder(binding.root) {
        private var previewJob: Job? = null

        fun cancelPreviousPreviews() {
            previewJob?.cancel()
            previewJob = null
        }

        fun launchPreview(block: suspend CoroutineScope.() -> Unit): Job {
            cancelPreviousPreviews()
            val job = parentScope.launch(dispatcherProvider.main, block = block)
            previewJob = job
            return job
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<RealmMyLibrary>, currentList: MutableList<RealmMyLibrary>) {
        super.onCurrentListChanged(previousList, currentList)
        textCache.clear()
        bitmapCache.evictAll()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (externalFilesDir == null) externalFilesDir = FileUtils.getExternalFilesDir(parent.context)
        val binding = ItemInlineResourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, parentScope, dispatcherProvider)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        textCache.clear()
        bitmapCache.evictAll()
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child) as? ViewHolder
            holder?.cancelPreviousPreviews()
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelPreviousPreviews()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val resource = getItem(position)
        val context = holder.itemView.context

        payloads.forEach { payloadList ->
            if (payloadList is List<*>) {
                payloadList.forEach { payload ->
                    when (payload) {
                        "TITLE" -> holder.binding.tvResourceTitle.text = resource.title ?: resource.resourceLocalAddress ?: ""
                        "ADDRESS" -> {
                            holder.binding.tvResourceTitle.text = resource.title ?: resource.resourceLocalAddress ?: ""
                            updateStatusAndPreview(holder, context, resource)
                        }
                        "STATUS" -> updateStatusAndPreview(holder, context, resource)
                    }
                }
            }
        }

        holder.binding.cardResource.setOnClickListener {
            onResourceClick(resource)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val resource = getItem(position)
        val context = holder.itemView.context

        holder.cancelPreviousPreviews()
        holder.binding.tvResourceTitle.text = resource.title ?: resource.resourceLocalAddress ?: ""
        updateStatusAndPreview(holder, context, resource)

        holder.binding.cardResource.setOnClickListener {
            onResourceClick(resource)
        }
    }

    private fun updateStatusAndPreview(holder: ViewHolder, context: Context, resource: RealmMyLibrary) {
        val binding = holder.binding
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
                externalFilesDir,
                "ole/${resource.id}/${resource.resourceLocalAddress}"
            )

            when {
                mimeType?.startsWith("image") == true -> showImagePreview(binding, context, resourceFile)
                mimeType?.startsWith("video") == true -> showVideoPreview(binding, context, resourceFile)
                mimeType?.contains("pdf") == true -> showPdfPreview(holder, resourceFile)
                mimeType?.startsWith("audio") == true -> showAudioPreview(holder, resourceFile)
                mimeType?.contains("csv") == true || resource.resourceLocalAddress?.endsWith(".csv") == true -> showCsvPreview(holder, resourceFile)
                mimeType?.startsWith("text") == true || resource.resourceLocalAddress?.endsWith(".txt") == true || resource.resourceLocalAddress?.endsWith(".md") == true -> showTextPreview(holder, resourceFile)
            }
        } else {
            binding.pbDownload.visibility = View.VISIBLE
        }

        binding.ivResourceIcon.setImageResource(
            ResourceOpener.getResourceTypeIcon(resource.resourceLocalAddress)
        )
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

    private fun showPdfPreview(holder: ViewHolder, file: File) {
        if (!file.exists()) return
        holder.launchPreview {
            val cacheKey = "${file.absolutePath}_${file.lastModified()}"
            val cachedBitmap = bitmapCache.get(cacheKey)
            val bitmap = if (cachedBitmap != null) {
                cachedBitmap
            } else {
                withContext(dispatcherProvider.io) {
                    try {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            PdfRenderer(fd).use { renderer ->
                                renderer.openPage(0).use { page ->
                                    val scale = 2
                                    createBitmap(page.width * scale, page.height * scale).also {
                                        page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }?.also { bitmapCache.put(cacheKey, it) }
            }
            if (bitmap != null) {
                holder.binding.ivResourcePreview.visibility = View.VISIBLE
                holder.binding.ivResourcePreview.scaleType = ImageView.ScaleType.FIT_CENTER
                holder.binding.ivResourcePreview.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                holder.binding.ivResourcePreview.setImageBitmap(bitmap)
            }
        }
    }

    private fun showAudioPreview(holder: ViewHolder, file: File) {
        holder.binding.audioPreviewContainer.visibility = View.VISIBLE
        if (!file.exists()) return
        holder.launchPreview {
            val cacheKey = "${file.absolutePath}_${file.lastModified()}"
            val cachedDuration = textCache[cacheKey]
            val durationText = if (cachedDuration != null) {
                cachedDuration
            } else {
                withContext(dispatcherProvider.io) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        val totalSeconds = durationMs / 1000
                        String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60)
                    } catch (e: Exception) {
                        ""
                    } finally {
                        retriever.release()
                    }
                }.also { textCache[cacheKey] = it }
            }
            holder.binding.tvAudioDuration.text = durationText
        }
    }

    private fun showCsvPreview(holder: ViewHolder, file: File) {
        if (!file.exists()) return
        holder.launchPreview {
            val cacheKey = "${file.absolutePath}_${file.lastModified()}"
            val cachedPreview = textCache[cacheKey]
            val preview = if (cachedPreview != null) {
                cachedPreview
            } else {
                withContext(dispatcherProvider.io) {
                    try {
                        val sb = StringBuilder()
                        CSVReaderBuilder(FileReader(file))
                            .withCSVParser(CSVParserBuilder().withSeparator(',').withQuoteChar('"').build())
                            .build().use { reader ->
                                var count = 0
                                for (row in reader) {
                                    if (count >= 5) break
                                    sb.appendLine(row.joinToString("  |  "))
                                    count++
                                }
                            }
                        sb.toString().trimEnd().takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                }?.also { textCache[cacheKey] = it }
            }
            if (!preview.isNullOrEmpty()) {
                holder.binding.tvTextPreview.visibility = View.VISIBLE
                holder.binding.tvTextPreview.text = preview
            }
        }
    }

    private fun showTextPreview(holder: ViewHolder, file: File) {
        if (!file.exists()) return
        holder.launchPreview {
            val cacheKey = "${file.absolutePath}_${file.lastModified()}"
            val cachedText = textCache[cacheKey]
            val text = if (cachedText != null) {
                cachedText
            } else {
                withContext(dispatcherProvider.io) {
                    try {
                        file.bufferedReader().useLines { it.take(8).joinToString("\n") }.takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                }?.also { textCache[cacheKey] = it }
            }
            if (!text.isNullOrEmpty()) {
                holder.binding.tvTextPreview.visibility = View.VISIBLE
                holder.binding.tvTextPreview.text = text
            }
        }
    }
}
