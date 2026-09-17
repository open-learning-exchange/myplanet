package org.ole.planet.myplanet.ui.teams.resources

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnResourcesUpdateListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LibraryType
import org.ole.planet.myplanet.utils.LibraryTypeClassifier
import org.ole.planet.myplanet.utils.PdfThumbnailLoader
import org.ole.planet.myplanet.utils.Utilities

class TeamResourcesAdapter(
    private val context: Context,
    private val canRemoveResources: Boolean,
    private val updateListener: OnResourcesUpdateListener,
    private val onRemoveResource: (MyLibrary, Int) -> Unit,
) : ListAdapter<MyLibrary, TeamResourcesAdapter.ViewHolderTeamResources>(ITEM_CALLBACK) {

    private var listener: OnHomeItemClickListener? = null
    private val externalFilesDir: File? by lazy { FileUtils.getExternalFilesDir(context) }
    private var adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!adapterScope.isActive) {
            adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
    }

    override fun onViewRecycled(holder: ViewHolderTeamResources) {
        super.onViewRecycled(holder)
        holder.cancelPreviewJob()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderTeamResources {
        val binding = RowTeamResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolderTeamResources(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderTeamResources, position: Int) {
        val adapterPosition = holder.bindingAdapterPosition
        if (adapterPosition == RecyclerView.NO_POSITION) return

        val resource = getItem(adapterPosition) ?: return
        val type = LibraryTypeClassifier.classify(resource)

        holder.binding.apply {
            tvTitle.text = resource.title
            tvMeta.text = buildMetaLine(resource, type)

            setCoverColor(coverContainer, type)
            ivTypeIcon.setImageResource(typeIconRes(type))

            holder.setPreviewJob(adapterScope.launch {
                bindCover(ivCoverPreview, ivTypeIcon, resource)
            })

            root.setOnClickListener {
                listener?.openLibraryDetailFragment(resource)
            }

            flRemoveContainer.visibility = if (canRemoveResources) View.VISIBLE else View.GONE
            ivRemove.setOnClickListener {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        onRemoveResource(getItem(currentPosition), currentPosition)
                    }
                }
            }
        }


    private fun setCoverColor(view: View, type: LibraryType) {
        val background = view.background?.mutate()
        if (background is GradientDrawable) {
            background.setColor(ContextCompat.getColor(context, typeColorRes(type)))
        }
    }

    private suspend fun bindCover(ivPreview: ImageView, ivTypeIcon: ImageView, library: MyLibrary) {
        val address = library.resourceLocalAddress
        val libraryId = library.id ?: library.resourceId
        val dir = externalFilesDir

        if (address.isNullOrBlank() || libraryId.isNullOrBlank() || dir == null) {
            showTypeIconOnly(ivPreview, ivTypeIcon)
            return
        }

        val file = FileUtils.getLibraryFile(dir, libraryId, address)
        val mimeType = Utilities.getMimeType(address)

        when {
            mimeType?.startsWith("image") == true -> showImagePreview(ivPreview, ivTypeIcon, file)
            mimeType?.startsWith("video") == true -> showVideoPreview(ivPreview, ivTypeIcon, file)
            mimeType?.contains("pdf") == true -> {
                val targetWidthPx = (84 * context.resources.displayMetrics.density).toInt()
                showPdfPreview(ivPreview, ivTypeIcon, file, targetWidthPx)
            }
            mimeType?.contains("html") == true -> {
                val resourceDir = File(dir, "ole/$libraryId")
                showHtmlPreview(ivPreview, ivTypeIcon, resourceDir)
            }
            else -> showTypeIconOnly(ivPreview, ivTypeIcon)
        }
    }

    private fun showTypeIconOnly(ivPreview: ImageView, ivTypeIcon: ImageView) {
        Glide.with(context).clear(ivPreview)
        ivPreview.visibility = View.GONE
        ivTypeIcon.visibility = View.VISIBLE
    }

    private suspend fun showImagePreview(ivPreview: ImageView, ivTypeIcon: ImageView, file: File) {
        val exists = withContext(Dispatchers.IO) { file.exists() }
        if (!exists) {
            showTypeIconOnly(ivPreview, ivTypeIcon)
            return
        }
        ivTypeIcon.visibility = View.GONE
        ivPreview.visibility = View.VISIBLE
        Glide.with(context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .placeholder(R.drawable.ole_logo)
            .error(R.drawable.ole_logo)
            .into(ivPreview)
    }

    private suspend fun showVideoPreview(ivPreview: ImageView, ivTypeIcon: ImageView, file: File) {
        val exists = withContext(Dispatchers.IO) { file.exists() }
        if (!exists) {
            showTypeIconOnly(ivPreview, ivTypeIcon)
            return
        }
        ivTypeIcon.visibility = View.GONE
        ivPreview.visibility = View.VISIBLE
        Glide.with(context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .placeholder(R.drawable.ole_logo)
            .error(R.drawable.ole_logo)
            .into(ivPreview)
    }

    private suspend fun showPdfPreview(ivPreview: ImageView, ivTypeIcon: ImageView, file: File, targetWidthPx: Int) {
        val exists = withContext(Dispatchers.IO) { file.exists() }
        if (!exists) {
            showTypeIconOnly(ivPreview, ivTypeIcon)
            return
        }
        Glide.with(context).clear(ivPreview)
        val bitmap = PdfThumbnailLoader.firstPageBitmap(file, org.ole.planet.myplanet.utils.DefaultDispatcherProvider(), targetWidthPx)
        if (bitmap != null) {
            ivTypeIcon.visibility = View.GONE
            ivPreview.visibility = View.VISIBLE
            ivPreview.setImageBitmap(bitmap)
        } else {
            showTypeIconOnly(ivPreview, ivTypeIcon)
        }
    }

    private suspend fun showHtmlPreview(ivPreview: ImageView, ivTypeIcon: ImageView, resourceDir: File) {
        val coverImage = withContext(Dispatchers.IO) { FileUtils.findHtmlCoverImage(resourceDir) }
        if (coverImage != null) {
            showImagePreview(ivPreview, ivTypeIcon, coverImage)
        } else {
            showTypeIconOnly(ivPreview, ivTypeIcon)
        }
    }

    private fun buildMetaLine(library: MyLibrary, type: LibraryType): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(typeLabelRes(type)))
        library.language?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    fun removeResourceAt(position: Int) {
        if (position < 0 || position >= currentList.size) return
        val newList = currentList.toMutableList()
        newList.removeAt(position)
        submitList(newList)
        updateListener.onResourceListUpdated()
    }

    class ViewHolderTeamResources(val binding: RowTeamResourceBinding) : RecyclerView.ViewHolder(binding.root) {
        private var previewJob: Job? = null

        fun setPreviewJob(job: Job?) {
            previewJob?.cancel()
            previewJob = job
        }

        fun cancelPreviewJob() = setPreviewJob(null)
    }

    companion object {
        private val ITEM_CALLBACK = DiffUtils.itemCallback<MyLibrary>(
            areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
            areContentsTheSame = { oldItem, newItem ->
                oldItem.title == newItem.title &&
                        oldItem.description == newItem.description &&
                        oldItem.mediaType == newItem.mediaType &&
                        oldItem.language == newItem.language
            }
        )

        private fun typeColorRes(type: LibraryType): Int = when (type) {
            LibraryType.PDF -> R.color.type_pdf
            LibraryType.VIDEO -> R.color.type_video
            LibraryType.AUDIO -> R.color.type_audio
            LibraryType.BOOK -> R.color.type_book
        }

        private fun typeIconRes(type: LibraryType): Int = when (type) {
            LibraryType.PDF -> R.drawable.ic_type_pdf
            LibraryType.VIDEO -> R.drawable.ic_type_video
            LibraryType.AUDIO -> R.drawable.ic_type_audio
            LibraryType.BOOK -> R.drawable.ic_type_book
        }

        private fun typeLabelRes(type: LibraryType): Int = when (type) {
            LibraryType.PDF -> R.string.filter_pdfs
            LibraryType.VIDEO -> R.string.filter_videos
            LibraryType.AUDIO -> R.string.filter_audio
            LibraryType.BOOK -> R.string.filter_books
        }
    }
}
