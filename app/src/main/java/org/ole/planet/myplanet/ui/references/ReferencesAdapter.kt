package org.ole.planet.myplanet.ui.references

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import org.ole.planet.myplanet.databinding.RowOtherInfoBinding
import org.ole.planet.myplanet.ui.references.ReferencesAdapter.ReferencesViewHolder
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString

class ReferencesAdapter(list: List<String>) : ListAdapter<String, ReferencesViewHolder>(DIFF_CALLBACK) {
    init {
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReferencesViewHolder {
        val binding = RowOtherInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReferencesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReferencesViewHolder, position: Int) {
        val jsonString = getItem(position)
        val obj = JsonUtils.gson.fromJson(jsonString, JsonObject::class.java)

        holder.binding.tvRefName.text = getString("name", obj).ifBlank { "—" }
        holder.binding.tvRefRelationship.text = getString("relationship", obj).ifBlank { "—" }
        holder.binding.tvRefPhone.text = getString("phone", obj).ifBlank { "—" }
        holder.binding.tvRefEmail.text = getString("email", obj).ifBlank { "—" }
    }

    class ReferencesViewHolder(val binding: RowOtherInfoBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<String>(
            { oldItem, newItem -> oldItem == newItem },
            { oldItem, newItem -> oldItem == newItem }
        )
    }
}