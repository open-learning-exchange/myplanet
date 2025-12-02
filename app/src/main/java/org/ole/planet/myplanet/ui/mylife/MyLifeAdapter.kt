
package org.ole.planet.myplanet.ui.mylife

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ItemMyLifeBinding
import org.ole.planet.myplanet.model.RealmMyLife

class MyLifeAdapter(
    private val listener: (RealmMyLife) -> Unit
) : ListAdapter<MyLifeData, MyLifeAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMyLifeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), listener, position)
    }

    class ViewHolder(private val binding: ItemMyLifeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(myLifeData: MyLifeData, listener: (RealmMyLife) -> Unit, position: Int) {
            val myLife = myLifeData.realmMyLife
            binding.title.text = myLife.title
            val context = binding.root.context
            val resourceId = context.resources.getIdentifier(myLife.imageId, "drawable", context.packageName)
            binding.image.setImageResource(resourceId)

            if (myLife.title == context.getString(R.string.my_survey)) {
                binding.count.visibility = View.VISIBLE
                binding.count.text = myLifeData.surveyCount.toString()
            } else {
                binding.count.visibility = View.GONE
            }

            val colorResId = if (position % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
            val color = ContextCompat.getColor(context, colorResId)
            itemView.setBackgroundColor(color)

            itemView.setOnClickListener { listener(myLife) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MyLifeData>() {
            override fun areItemsTheSame(oldItem: MyLifeData, newItem: MyLifeData): Boolean {
                return oldItem.realmMyLife._id == newItem.realmMyLife._id
            }

            override fun areContentsTheSame(oldItem: MyLifeData, newItem: MyLifeData): Boolean {
                return oldItem == newItem
            }
        }
    }
}
