package org.ole.planet.myplanet.ui.enterprises

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.RowFinanceBinding
import org.ole.planet.myplanet.model.TransactionData
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class FinanceAdapter(
    private val context: Context,
) : ListAdapter<TransactionData, FinanceAdapter.FinanceViewHolder>(TransactionDataDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FinanceViewHolder {
        val binding = RowFinanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FinanceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FinanceViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        binding.date.text = item.date?.let { formatDate(it, "MMM dd, yyyy") } ?: ""
        binding.note.text = item.description
        binding.debit.setTextColor(Color.BLACK)
        binding.credit.setTextColor(Color.BLACK)
        if (TextUtils.equals(item.type?.lowercase(Locale.getDefault()), "debit")) {
            binding.debit.text = context.getString(R.string.number_placeholder, item.amount)
            binding.credit.text = context.getString(R.string.message_placeholder, " -")
        } else {
            binding.credit.text = context.getString(R.string.number_placeholder, item.amount)
            binding.debit.text = context.getString(R.string.message_placeholder, " -")
        }
        binding.balance.text = item.balance.toString()
        updateBackgroundColor(binding.llayout, position)
    }

    private fun updateBackgroundColor(layout: LinearLayout, position: Int) {
        if (position % 2 < 1) {
            val border = GradientDrawable()
            border.setColor(-0x1) //white background
            border.setStroke(1, ContextCompat.getColor(context, R.color.black_overlay))
            border.gradientType = GradientDrawable.LINEAR_GRADIENT
            val layers = arrayOf<Drawable>(border)
            val layerDrawable = LayerDrawable(layers)
            layerDrawable.setLayerInset(0, -10, 0, -10, 0)
            layout.background = layerDrawable
        }
    }

    class FinanceViewHolder(val binding: RowFinanceBinding) : RecyclerView.ViewHolder(
        binding.root
    )

    private class TransactionDataDiffCallback : DiffUtil.ItemCallback<TransactionData>() {
        override fun areItemsTheSame(oldItem: TransactionData, newItem: TransactionData): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransactionData, newItem: TransactionData): Boolean {
            return oldItem == newItem
        }
    }
}
