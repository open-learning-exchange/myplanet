package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemInlineResourceBinding
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.PdfThumbnailLoader
import org.ole.planet.myplanet.utils.ResourceOpener
import org.ole.planet.myplanet.utils.ResourcesPreviewLoader
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

class InlineResourceAdapter(
    private val previewLoader: ResourcesPreviewLoader,
    private val dispatcherProvider: DispatcherProvider,
    private val onResourceClick: (MyLibrary) -> Unit
) : ListAdapter<MyLibrary, InlineResourceAdapter.ViewHolder>(
    DiffUtils.itemCallback<MyLibrary>(
        areItemsTheSame = { old, new -> old.id == new.id },
        areContentsTheSame = { old, new ->
            old.resourceLocalAddress == new.resourceLocalAddress &&
                old.title == new.title &&
                old.isResourceOffline() == new.isResourceOffline()
        },
        getChangePayload = { old, new ->
            val payloads = mutableListOf<String>()
            if (old.title != new.title) payloads.add(PAYLOAD_TITLE)
            if (old.resourceLocalAddress != new.resourceLocalAddress) payloads.add(PAYLOAD_ADDRESS)
            if (old.isResourceOffline() != new.isResourceOffline()) payloads.add(PAYLOAD_STATUS)
            if (payloads.isEmpty()) null else payloads
        }
    )
) {

    private var externalFilesDir: File? = null
    private val textCache = mutableMapOf<String, String>()
    private val htmlCoverCache = mutableMapOf<String, File?>()

    private var adapterScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!adapterScope.isActive) {
            adapterScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
        }
    }

    class ViewHolder(val binding: ItemInlineResourceBinding) : RecyclerView.ViewHolder(binding.root) {
        @get:VisibleForTesting
        internal var previewJob: Job? = null

        fun cancelPreviousPreviews() {
            previewJob?.cancel()
            previewJob = null
        }

        fun setPreviewJob(job: Job) {
            cancelPreviousPreviews()
            previewJob = job
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<MyLibrary>, currentList: MutableList<MyLibrary>) {
        super.onCurrentListChanged(previousList, currentList)
        val dir = externalFilesDir ?: return
        val currentMap = currentList.associateBy { it.id }

        previousList.forEach { prev ->
            val current = currentMap[prev.id]
            if (current == null || current.resourceLocalAddress != prev.resourceLocalAddress) {
                val address = prev.resourceLocalAddress ?: return@forEach
                val file = FileUtils.getLibraryFile(dir, prev.id, address)
                val prefix = file.absolutePath
                textCache.keys.removeAll { it.startsWith(prefix) }
                htmlCoverCache.remove(prev.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        if (externalFilesDir == null) externalFilesDir = FileUtils.getExternalFilesDir(parent.context)
        val binding = ItemInlineResourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
        textCache.clear()
        htmlCoverCache.clear()
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
                        PAYLOAD_TITLE -> holder.binding.tvResourceTitle.text = resource.title ?: resource.resourceLocalAddress ?: ""
                        PAYLOAD_ADDRESS -> {
                            holder.binding.tvResourceTitle.text = resource.title ?: resource.resourceLocalAddress ?: ""
                            updateStatusAndPreview(holder, context, resource)
                        }
                        PAYLOAD_STATUS -> updateStatusAndPreview(holder, context, resource)
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

    private fun updateStatusAndPreview(holder: ViewHolder, context: Context, resource: MyLibrary) {
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

            holder.setPreviewJob(adapterScope.launch {
                when {
                    mimeType?.startsWith("image") == true -> showImagePreview(binding, context, resourceFile)
                    mimeType?.startsWith("video") == true -> showVideoPreview(binding, context, resourceFile)
                    mimeType?.contains("pdf") == true -> showPdfPreview(holder, resourceFile)
                    mimeType?.startsWith("audio") == true -> showAudioPreview(holder, resourceFile)
                    mimeType?.contains("html") == true -> showHtmlPreview(binding, context, resource.id, File(externalFilesDir, "ole/${resource.id}"))
                    mimeType?.contains("csv") == true || resource.resourceLocalAddress?.endsWith(".csv") == true -> showCsvPreview(holder, resourceFile)
                    mimeType?.startsWith("text") == true || resource.resourceLocalAddress?.endsWith(".txt") == true || resource.resourceLocalAddress?.endsWith(".md") == true -> showTextPreview(holder, resourceFile)
                }
            })
        } else {
            binding.pbDownload.visibility = View.VISIBLE
        }

        binding.ivResourceIcon.setImageResource(
            ResourceOpener.getResourceTypeIcon(resource.resourceLocalAddress)
        )
    }

    private suspend fun showImagePreview(binding: ItemInlineResourceBinding, context: Context, file: File) {
        val exists = withContext(dispatcherProvider.io) { file.exists() }
        if (exists) {
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

    private suspend fun showVideoPreview(binding: ItemInlineResourceBinding, context: Context, file: File) {
        binding.videoThumbnailContainer.visibility = View.VISIBLE
        val exists = withContext(dispatcherProvider.io) { file.exists() }
        if (exists) {
            Glide.with(context)
                .load(file)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(binding.ivVideoThumbnail)
        }
    }

    private suspend fun showPdfPreview(holder: ViewHolder, file: File) {
        val exists = withContext(dispatcherProvider.io) { file.exists() }
        if (!exists) return
        val context = holder.itemView.context
        val targetWidthPx = (PDF_PREVIEW_WIDTH_DP * context.resources.displayMetrics.density).toInt()
        Glide.with(context).clear(holder.binding.ivResourcePreview)
        val bitmap = PdfThumbnailLoader.firstPageBitmap(file, dispatcherProvider, targetWidthPx)
        if (bitmap != null) {
            holder.binding.ivResourcePreview.visibility = View.VISIBLE
            holder.binding.ivResourcePreview.scaleType = ImageView.ScaleType.FIT_CENTER
            holder.binding.ivResourcePreview.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.binding.ivResourcePreview.setImageBitmap(bitmap)
        }
    }

    private suspend fun showHtmlPreview(binding: ItemInlineResourceBinding, context: Context, resourceId: String, resourceDir: File) {
        val coverImage = if (htmlCoverCache.containsKey(resourceId)) {
            htmlCoverCache.getValue(resourceId)
        } else {
            withContext(dispatcherProvider.io) { FileUtils.findHtmlCoverImage(resourceDir) }.also {
                htmlCoverCache[resourceId] = it
            }
        }
        if (coverImage != null) {
            binding.ivResourcePreview.visibility = View.VISIBLE
            Glide.with(context)
                .load(coverImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.ole_logo)
                .error(R.drawable.ole_logo)
                .into(binding.ivResourcePreview)
        }
    }

    private suspend fun showAudioPreview(holder: ViewHolder, file: File) {
        holder.binding.audioPreviewContainer.visibility = View.VISIBLE
        val cacheKey = getFileCacheKeyIfExist(file) ?: return
        val cachedDuration = textCache[cacheKey]
        val durationText = if (cachedDuration != null) {
            cachedDuration
        } else {
            previewLoader.getAudioPreview(file).also { textCache[cacheKey] = it }
        }
        holder.binding.tvAudioDuration.text = durationText
    }

    private suspend fun showCsvPreview(holder: ViewHolder, file: File) {
        val cacheKey = getFileCacheKeyIfExist(file) ?: return
        val cachedPreview = textCache[cacheKey]
        val preview = if (cachedPreview != null) {
            cachedPreview
        } else {
            previewLoader.getCsvPreview(file)?.also { textCache[cacheKey] = it }
        }
        if (!preview.isNullOrEmpty()) {
            holder.binding.tvTextPreview.visibility = View.VISIBLE
            holder.binding.tvTextPreview.text = preview
        }
    }

    private suspend fun showTextPreview(holder: ViewHolder, file: File) {
        val cacheKey = getFileCacheKeyIfExist(file) ?: return
        val cachedText = textCache[cacheKey]
        val text = if (cachedText != null) {
            cachedText
        } else {
            previewLoader.getTextPreview(file)?.also { textCache[cacheKey] = it }
        }
        if (!text.isNullOrEmpty()) {
            holder.binding.tvTextPreview.visibility = View.VISIBLE
            holder.binding.tvTextPreview.text = text
        }
    }

    private suspend fun getFileCacheKeyIfExist(file: File): String? = withContext(dispatcherProvider.io) {
        if (file.exists()) {
            getCacheKey(file)
        } else {
            null
        }
    }

    private fun getCacheKey(file: File): String = "${file.absolutePath}_${file.lastModified()}_${file.length()}"

    companion object {
        const val PAYLOAD_TITLE = "PAYLOAD_TITLE"
        const val PAYLOAD_ADDRESS = "PAYLOAD_ADDRESS"
        const val PAYLOAD_STATUS = "PAYLOAD_STATUS"
        private const val PDF_PREVIEW_WIDTH_DP = 240
    }
}
