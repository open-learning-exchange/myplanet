package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
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
import org.ole.planet.myplanet.callback.OnLibraryItemSelectedListener
import org.ole.planet.myplanet.databinding.ItemLibraryGridBinding
import org.ole.planet.myplanet.databinding.ItemLibraryListBinding
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LibraryType
import org.ole.planet.myplanet.utils.LibraryTypeClassifier
import org.ole.planet.myplanet.utils.ListViewMode
import org.ole.planet.myplanet.utils.PdfThumbnailLoader
import org.ole.planet.myplanet.utils.StableIdGenerator
import org.ole.planet.myplanet.utils.Utilities

class ResourcesAdapter(
    private val context: Context,
    private var isGuest: Boolean,
    private var openedResourceIds: Set<String>,
    private var currentUserName: String? = null,
    private var viewMode: ListViewMode = ListViewMode.GRID,
    private val dispatcherProvider: DispatcherProvider,
    private val onEditClick: ((ResourceListModel) -> Unit)? = null
) : ListAdapter<ResourceListModel, RecyclerView.ViewHolder>(ITEM_CALLBACK) {

    private val selectedItemIds = mutableSetOf<String>()
    private val selectedItemsMap = LinkedHashMap<String, ResourceItem>()
    private var listener: OnLibraryItemSelectedListener? = null
    private val locallyOfflineIds = mutableSetOf<String>()
    private val externalFilesDir: File? by lazy { FileUtils.getExternalFilesDir(context) }
    private var adapterScope = CoroutineScope(SupervisorJob() + dispatcherProvider.main)
    private val htmlCoverCache = mutableMapOf<String, File?>()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val listModel = getItem(position)
        val id = StableIdGenerator.generateStringId(listModel.item.id)
        return if (id != RecyclerView.NO_ID) id else StableIdGenerator.generateFallbackId(listModel)
    }

    companion object {
        const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
        const val PAYLOAD_VIEW_MODE = "PAYLOAD_VIEW_MODE"
        const val PAYLOAD_IDENTITY = "PAYLOAD_IDENTITY"
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
        private const val GRID_COVER_WIDTH_DP = 84
        private const val LIST_COVER_WIDTH_DP = 44

        private val ITEM_CALLBACK = DiffUtils.standardItemCallback<ResourceListModel>(
            idSelector = { it.item.id ?: "" },
            contentSelector = {
                listOf(
                    it.item.title,
                    it.item.description,
                    it.item._rev,
                    it.item.isOffline,
                    it.item.averageRating,
                    it.item.timesRated,
                    it.isOpened,
                    it.isLocallyOffline,
                    it.tags,
                    it.library.language,
                    it.library.addedBy,
                    it.library.resourceLocalAddress,
                    it.library.resourceRemoteAddress,
                    it.library.mediaType
                )
            },
            payloadSelector = { oldItem, newItem ->
                val payloads = mutableListOf<String>()
                if (oldItem.isOpened != newItem.isOpened || oldItem.item.isOffline != newItem.item.isOffline || oldItem.isLocallyOffline != newItem.isLocallyOffline) {
                    payloads.add(PAYLOAD_SELECTION)
                }
                payloads.ifEmpty { null }
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

    fun setViewMode(mode: ListViewMode, onChanged: (() -> Unit)? = null) {
        if (viewMode != mode) {
            viewMode = mode
            notifyItemRangeChanged(0, itemCount, PAYLOAD_VIEW_MODE)
        }
        onChanged?.invoke()
    }

    fun updateIdentity(isGuest: Boolean, currentUserName: String?) {
        var changed = false
        if (this.isGuest != isGuest) {
            this.isGuest = isGuest
            changed = true
        }
        if (this.currentUserName != currentUserName) {
            this.currentUserName = currentUserName
            changed = true
        }
        if (changed) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_IDENTITY)
        }
    }

    fun markItemAsOffline(id: String) {
        if (locallyOfflineIds.add(id)) {
            val index = currentList.indexOfFirst { it.item.id == id }
            if (index != -1) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    fun setListener(listener: OnLibraryItemSelectedListener?) {
        this.listener = listener
    }

    fun setLibraryList(libraryList: List<ResourceListModel?>, onComplete: (() -> Unit)? = null) {
        val updatedList = libraryList.filterNotNull().map {
            it.copy(
                isOpened = openedResourceIds.contains(it.item.id),
                isLocallyOffline = locallyOfflineIds.contains(it.item.id)
            )
        }
        submitList(updatedList, onComplete)
    }

    override fun getItemViewType(position: Int): Int {
        return if (viewMode == ListViewMode.GRID) VIEW_TYPE_GRID else VIEW_TYPE_LIST
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
    }

    override fun onCurrentListChanged(previousList: MutableList<ResourceListModel>, currentList: MutableList<ResourceListModel>) {
        super.onCurrentListChanged(previousList, currentList)
        val currentMap = currentList.associateBy { it.library.id }
        previousList.forEach { prev ->
            val id = prev.library.id
            val current = currentMap[id]
            if (current == null || current.library.resourceLocalAddress != prev.library.resourceLocalAddress) {
                htmlCoverCache.remove(id)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is GridViewHolder -> holder.cancelPreviewJob()
            is ListViewHolder -> holder.cancelPreviewJob()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_GRID) {
            GridViewHolder(ItemLibraryGridBinding.inflate(inflater, parent, false))
        } else {
            ListViewHolder(ItemLibraryListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val model = getItem(position) ?: return
        when (holder) {
            is GridViewHolder -> bindGrid(holder, model)
            is ListViewHolder -> bindList(holder, model)
        }
    }

    fun getLocallyOfflineIds(): Set<String> = locallyOfflineIds.toSet()

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }
        val model = getItem(position) ?: return
        val flatPayloads = payloads.flatMap { it as? List<*> ?: listOf(it) }

        var partialHandled = false
        if (flatPayloads.contains(PAYLOAD_SELECTION) || flatPayloads.contains(PAYLOAD_IDENTITY)) {
            when (holder) {
                is GridViewHolder -> {
                    bindSelectionAndDownload(holder.binding.checkbox, holder.binding.ivDownloaded, model)
                    if (flatPayloads.contains(PAYLOAD_IDENTITY)) bindClicks(holder.itemView, holder.binding.checkbox, model)
                }
                is ListViewHolder -> {
                    bindSelectionAndDownload(holder.binding.checkbox, holder.binding.ivDownloaded, model)
                    if (flatPayloads.contains(PAYLOAD_IDENTITY)) bindClicks(holder.itemView, holder.binding.checkbox, model)
                }
            }
            partialHandled = true
        }

        if (flatPayloads.contains(PAYLOAD_VIEW_MODE) || !partialHandled) {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindGrid(holder: GridViewHolder, model: ResourceListModel) {
        val binding = holder.binding
        val type = LibraryTypeClassifier.classify(model.library)
        holder.setPreviewJob(bindCover(binding.coverContainer, binding.ivCoverPreview, binding.ivTypeIcon, type, model, GRID_COVER_WIDTH_DP))
        binding.title.text = model.item.title
        binding.tvMeta.text = buildMetaLine(model, type)
        bindSelectionAndDownload(binding.checkbox, binding.ivDownloaded, model)
        bindClicks(holder.itemView, binding.checkbox, model)
    }

    private fun bindList(holder: ListViewHolder, model: ResourceListModel) {
        val binding = holder.binding
        val type = LibraryTypeClassifier.classify(model.library)
        holder.setPreviewJob(bindCover(binding.coverContainer, binding.ivCoverPreview, binding.ivTypeIcon, type, model, LIST_COVER_WIDTH_DP))
        binding.title.text = model.item.title
        binding.tvMeta.text = buildMetaLine(model, type)
        bindSelectionAndDownload(binding.checkbox, binding.ivDownloaded, model)
        bindClicks(holder.itemView, binding.checkbox, model)
    }

    private fun setCoverColor(view: View, type: LibraryType) {
        val background = view.background?.mutate()
        if (background is GradientDrawable) {
            background.setColor(ContextCompat.getColor(context, typeColorRes(type)))
        }
    }

    private fun bindCover(
        coverContainer: View,
        ivPreview: ImageView,
        ivTypeIcon: ImageView,
        type: LibraryType,
        model: ResourceListModel,
        coverWidthDp: Int
    ): Job? {
        setCoverColor(coverContainer, type)
        ivTypeIcon.setImageResource(typeIconRes(type))

        val isOffline = model.item.isOffline || locallyOfflineIds.contains(model.item.id) || model.isLocallyOffline
        val address = model.library.resourceLocalAddress
        val libraryId = model.library.id
        val dir = externalFilesDir
        if (!isOffline || address.isNullOrBlank() || libraryId.isNullOrBlank() || dir == null) {
            showTypeIconOnly(ivPreview, ivTypeIcon)
            return null
        }

        val file = FileUtils.getLibraryFile(dir, libraryId, address)
        val mimeType = Utilities.getMimeType(address)
        return when {
            mimeType?.startsWith("image") == true -> {
                showImagePreview(ivPreview, ivTypeIcon, file)
                null
            }
            mimeType?.startsWith("video") == true -> {
                showVideoPreview(ivPreview, ivTypeIcon, file)
                null
            }
            mimeType?.contains("pdf") == true -> {
                showTypeIconOnly(ivPreview, ivTypeIcon)
                val targetWidthPx = (coverWidthDp * context.resources.displayMetrics.density).toInt()
                adapterScope.launch { showPdfPreview(ivPreview, ivTypeIcon, file, targetWidthPx) }
            }
            mimeType?.contains("html") == true -> {
                showTypeIconOnly(ivPreview, ivTypeIcon)
                val resourceDir = File(dir, "ole/$libraryId")
                adapterScope.launch { showHtmlPreview(ivPreview, ivTypeIcon, libraryId, resourceDir) }
            }
            else -> {
                showTypeIconOnly(ivPreview, ivTypeIcon)
                null
            }
        }
    }

    private fun showTypeIconOnly(ivPreview: ImageView, ivTypeIcon: ImageView) {
        Glide.with(context).clear(ivPreview)
        ivPreview.visibility = View.GONE
        ivTypeIcon.visibility = View.VISIBLE
    }

    private fun showImagePreview(ivPreview: ImageView, ivTypeIcon: ImageView, file: File) {
        if (!file.exists()) {
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

    private fun showVideoPreview(ivPreview: ImageView, ivTypeIcon: ImageView, file: File) {
        if (!file.exists()) {
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
        if (!file.exists()) {
            showTypeIconOnly(ivPreview, ivTypeIcon)
            return
        }
        Glide.with(context).clear(ivPreview)
        val bitmap = PdfThumbnailLoader.firstPageBitmap(file, dispatcherProvider, targetWidthPx)

        if (bitmap != null) {
            ivTypeIcon.visibility = View.GONE
            ivPreview.visibility = View.VISIBLE
            ivPreview.setImageBitmap(bitmap)
        } else {
            showTypeIconOnly(ivPreview, ivTypeIcon)
        }
    }

    private suspend fun showHtmlPreview(ivPreview: ImageView, ivTypeIcon: ImageView, libraryId: String, resourceDir: File) {
        val coverImage = if (htmlCoverCache.containsKey(libraryId)) {
            htmlCoverCache.getValue(libraryId)
        } else {
            withContext(dispatcherProvider.io) { FileUtils.findHtmlCoverImage(resourceDir) }.also {
                htmlCoverCache[libraryId] = it
            }
        }
        if (coverImage != null) {
            showImagePreview(ivPreview, ivTypeIcon, coverImage)
        } else {
            showTypeIconOnly(ivPreview, ivTypeIcon)
        }
    }

    private fun buildMetaLine(model: ResourceListModel, type: LibraryType): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(typeLabelRes(type)))
        val localPath = model.item.resourceLocalAddress
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists()) {
                parts.add(FileUtils.formatSize(context, file.length()))
            }
        }
        model.library.language?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    private fun bindSelectionAndDownload(checkbox: CheckBox, ivDownloaded: ImageView, model: ResourceListModel) {
        checkbox.isChecked = selectedItemIds.contains(model.item.id)
        checkbox.visibility = if (isGuest) View.GONE else View.VISIBLE

        val isResourceOpened = openedResourceIds.contains(model.item.id) || model.isOpened
        val isOffline = model.item.isOffline || locallyOfflineIds.contains(model.item.id) || model.isLocallyOffline

        ivDownloaded.setImageResource(if (isOffline) R.drawable.ic_check_circle else R.drawable.ic_download)
        ImageViewCompat.setImageTintList(
            ivDownloaded,
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, if (isOffline) R.color.list_status_completed_text else R.color.hint_color)
            )
        )
        ivDownloaded.contentDescription = if (isOffline) {
            context.getString(R.string.view)
        } else {
            context.getString(R.string.download)
        }
        ivDownloaded.visibility = if (isResourceOpened && isOffline) View.INVISIBLE else View.VISIBLE
    }

    private fun bindClicks(itemView: View, checkbox: CheckBox, model: ResourceListModel) {
        itemView.setOnClickListener {
            openLibrary(model)
        }

        val isOwnResource = !currentUserName.isNullOrBlank() &&
                model.library.addedBy == currentUserName &&
                !isGuest
        itemView.setOnLongClickListener {
            if (isOwnResource) {
                showEditMenu(itemView, model)
                true
            } else {
                false
            }
        }

        if (!isGuest) {
            checkbox.setOnClickListener { view: View ->
                checkbox.contentDescription = context.getString(R.string.select_res_course, model.item.title ?: "")
                val isChecked = (view as CheckBox).isChecked
                model.item.id?.let { itemId ->
                    if (isChecked) {
                        selectedItemIds.add(itemId)
                        selectedItemsMap[itemId] = model.item
                    } else {
                        selectedItemIds.remove(itemId)
                        selectedItemsMap.remove(itemId)
                    }
                }
                listener?.onSelectedListChange(selectedItemsMap.values.toList())
            }
        } else {
            checkbox.setOnClickListener(null)
        }
    }

    private fun showEditMenu(anchor: View, model: ResourceListModel) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(context.getString(R.string.edit_resource))
        popup.setOnMenuItemClickListener {
            onEditClick?.invoke(model)
            true
        }
        popup.show()
    }

    fun areAllSelected(): Boolean {
        return currentList.isNotEmpty() && selectedItemIds.size == currentList.size
    }

    fun selectAllItems(selectAll: Boolean) {
        if (selectAll) {
            currentList.forEachIndexed { index, model ->
                model.item.id?.let { itemId ->
                    if (selectedItemIds.add(itemId)) {
                        selectedItemsMap[itemId] = model.item
                        notifyItemChanged(index, PAYLOAD_SELECTION)
                    }
                }
            }
        } else {
            currentList.forEachIndexed { index, model ->
                model.item.id?.let { itemId ->
                    if (selectedItemIds.remove(itemId)) {
                        selectedItemsMap.remove(itemId)
                        notifyItemChanged(index, PAYLOAD_SELECTION)
                    }
                }
            }
        }

        listener?.onSelectedListChange(selectedItemsMap.values.toList())
    }

    private fun openLibrary(model: ResourceListModel) {
        listener?.onResourceClicked(model.item)
    }

    fun setOpenedResourceIds(newOpenedResourceIds: Set<String>) {
        val oldOpenedResourceIds = this.openedResourceIds
        this.openedResourceIds = newOpenedResourceIds
        currentList.forEachIndexed { index, model ->
            val wasOpened = oldOpenedResourceIds.contains(model.item.id)
            val isOpened = newOpenedResourceIds.contains(model.item.id)
            if (wasOpened != isOpened) {
                notifyItemChanged(index, PAYLOAD_SELECTION)
            }
        }
    }

    internal class GridViewHolder(val binding: ItemLibraryGridBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var previewJob: Job? = null

        fun setPreviewJob(job: Job?) {
            previewJob?.cancel()
            previewJob = job
        }

        fun cancelPreviewJob() = setPreviewJob(null)
    }

    internal class ListViewHolder(val binding: ItemLibraryListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var previewJob: Job? = null

        fun setPreviewJob(job: Job?) {
            previewJob?.cancel()
            previewJob = job
        }

        fun cancelPreviewJob() = setPreviewJob(null)
    }
}
