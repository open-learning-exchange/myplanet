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

class ReferencesAdapter(list: List<String>) : ListAdapter<ReferenceRow, ReferencesViewHolder>(DIFF_CALLBACK) {
    init {
        submitJsonList(list)
    }

    fun submitJsonList(list: List<String>) {
        val rows = list.map { jsonString ->
            var obj: JsonObject? = null
            try {
                obj = JsonUtils.gson.fromJson(jsonString, JsonObject::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (obj == null) {
                obj = JsonObject()
            }
            ReferenceRow(
                name = getString("name", obj).ifBlank { "—" },
                relationship = getString("relationship", obj).ifBlank { "—" },
                phone = getString("phone", obj).ifBlank { "—" },
                email = getString("email", obj).ifBlank { "—" }
            )
        }
        submitList(rows)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReferencesViewHolder {
        val binding = RowOtherInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReferencesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReferencesViewHolder, position: Int) {
        val row = getItem(position)
        holder.binding.tvRefName.text = row.name
        holder.binding.tvRefRelationship.text = row.relationship
        holder.binding.tvRefPhone.text = row.phone
        holder.binding.tvRefEmail.text = row.email
    }

    class ReferencesViewHolder(val binding: RowOtherInfoBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<ReferenceRow>(
            { oldItem, newItem -> oldItem.name == newItem.name },
            { oldItem, newItem -> oldItem == newItem }
        )
    }
}

data class ReferenceRow(
    val name: String,
    val relationship: String,
    val phone: String,
    val email: String
)
