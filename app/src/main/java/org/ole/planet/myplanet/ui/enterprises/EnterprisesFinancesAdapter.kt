package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowFinanceBinding
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.Transaction
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.ImageViewerUtils
import org.ole.planet.myplanet.utils.TimeUtils.formatDate

class EnterprisesFinancesAdapter(
    private val context: Context,
) : ListAdapter<Transaction, EnterprisesFinancesAdapter.FinanceViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.id == newItem.id },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {
    private val attachmentExistsCache = HashMap<String, Boolean>()

    override fun onCurrentListChanged(
        previousList: MutableList<Transaction>,
        currentList: MutableList<Transaction>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        attachmentExistsCache.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FinanceViewHolder {
        val binding = RowFinanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FinanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FinanceViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        binding.date.text = item.date?.let { formatDate(it, "MMM dd, yyyy") } ?: ""
        binding.note.text = item.description
        if (item.type.equals("debit", ignoreCase = true)) {
            binding.debit.text = context.getString(R.string.number_placeholder, item.amount)
            binding.credit.text = context.getString(R.string.message_placeholder, " -")
        } else {
            binding.credit.text = context.getString(R.string.number_placeholder, item.amount)
            binding.debit.text = context.getString(R.string.message_placeholder, " -")
        }
        binding.balance.text = item.balance.toString()
        bindFinanceImage(binding, item)
        updateBackgroundColor(holder, position)
    }

    override fun onViewRecycled(holder: FinanceViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(context).clear(holder.binding.financeImage)
        holder.binding.financeImage.setOnClickListener(null)
    }

    private fun bindFinanceImage(binding: RowFinanceBinding, item: Transaction) {
        val imageFile = MyTeam.getAttachmentFile(context, item.id, item.imageName)
        if (imageFile != null && attachmentExistsCache.getOrPut(imageFile.absolutePath) { imageFile.exists() }) {
            binding.financeImage.visibility = View.VISIBLE
            Glide.with(context)
                .load(imageFile)
                .placeholder(R.drawable.ic_loading)
                .error(R.drawable.ic_loading)
                .into(binding.financeImage)
            binding.financeImage.setOnClickListener {
                ImageViewerUtils.showZoomableImage(context, imageFile.absolutePath)
            }
        } else {
            binding.financeImage.visibility = View.GONE
        }
    }

    private fun updateBackgroundColor(holder: FinanceViewHolder, position: Int) {
        if (position % 2 < 1) {
            holder.binding.llayout.background = holder.alternateColor
        } else {
            holder.binding.llayout.background = null
        }
    }

    class FinanceViewHolder(val binding: RowFinanceBinding) : RecyclerView.ViewHolder(
        binding.root
    ) {
        val alternateColor: Drawable by lazy {
            val border = GradientDrawable()
            border.setColor(-0x1) //white background
            border.setStroke(1, ContextCompat.getColor(binding.root.context, R.color.black_overlay))
            border.gradientType = GradientDrawable.LINEAR_GRADIENT
            val layerDrawable = LayerDrawable(arrayOf<Drawable>(border))
            layerDrawable.setLayerInset(0, -10, 0, -10, 0)
            layerDrawable.mutate()
        }
    }
}
