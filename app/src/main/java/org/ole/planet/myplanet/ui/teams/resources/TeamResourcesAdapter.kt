package org.ole.planet.myplanet.ui.teams.resources

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.OnResourcesUpdateListener
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.ui.resources.ResourceCardHelper
import org.ole.planet.myplanet.utils.DefaultDispatcherProvider
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LibraryTypeClassifier

class TeamResourcesAdapter(
    private val context: Context,
    private val canRemoveResources: Boolean,
    private val updateListener: OnResourcesUpdateListener,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val onRemoveResource: (MyLibrary, Int) -> Unit,
) : ListAdapter<MyLibrary, TeamResourcesAdapter.ViewHolderTeamResources>(ITEM_CALLBACK) {

    private var listener: OnHomeItemClickListener? = null
    private val externalFilesDir: File? by lazy { FileUtils.getExternalFilesDir(context) }
    private var adapterScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
    private val htmlCoverCache = mutableMapOf<String, File?>()
    private val fileLengthCache = mutableMapOf<String, Long?>()

    init {
        if (context is OnHomeItemClickListener) {
            listener = context
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!adapterScope.isActive) {
            adapterScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        adapterScope.cancel()
        htmlCoverCache.clear()
        fileLengthCache.clear()
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
        if (position < 0 || position >= itemCount) return
        val resource = getItem(position) ?: return
        val type = LibraryTypeClassifier.classify(resource)

        holder.binding.apply {
            tvTitle.text = resource.title
            tvMeta.text = ResourceCardHelper.buildMetaLine(context, type, resource.language)

            ResourceCardHelper.setCoverColor(coverContainer, type)
            ivTypeIcon.setImageResource(ResourceCardHelper.typeIconRes(type))

            ResourceCardHelper.showTypeIconOnly(context, ivCoverPreview, ivTypeIcon)

            holder.setPreviewJob(adapterScope.launch {
                ResourceCardHelper.bindCover(
                    context = context,
                    ivPreview = ivCoverPreview,
                    ivTypeIcon = ivTypeIcon,
                    isOffline = true,
                    address = resource.resourceLocalAddress,
                    libraryId = resource.id.ifEmpty { resource.resourceId },
                    externalFilesDir = externalFilesDir,
                    coverWidthDp = 84,
                    dispatcherProvider = dispatcherProvider,
                    htmlCoverCache = htmlCoverCache,
                    fileLengthCache = fileLengthCache
                )
            })

            root.setOnClickListener {
                listener?.openLibraryDetailFragment(resource)
            }

            flRemoveContainer.visibility = if (canRemoveResources) View.VISIBLE else View.GONE
            flRemoveContainer.contentDescription = context.getString(R.string.remove)
            flRemoveContainer.setOnClickListener {
                val currentPosition = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: position
                if (currentPosition >= 0 && currentPosition < itemCount) {
                    onRemoveResource(getItem(currentPosition), currentPosition)
                }
            }
        }
    }

    fun removeResourceAt(position: Int) {
        if (position < 0 || position >= currentList.size) return
        val newList = currentList.toMutableList()
        newList.removeAt(position)
        submitList(newList) {
            updateListener.onResourceListUpdated()
        }
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
                        oldItem.mediaType == newItem.mediaType &&
                        oldItem.language == newItem.language
            }
        )
    }
}
