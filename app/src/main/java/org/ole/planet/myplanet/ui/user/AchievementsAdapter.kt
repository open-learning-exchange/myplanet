package org.ole.planet.myplanet.ui.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import org.ole.planet.myplanet.databinding.RowOtherInfoBinding
import org.ole.planet.myplanet.ui.user.AchievementsAdapter.AchievementsViewHolder
import org.ole.planet.myplanet.utils.DiffUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString

class AchievementsAdapter(list: List<String>) : ListAdapter<ReferenceRow, AchievementsViewHolder>(DIFF_CALLBACK) {
    init {
        submitJsonList(list)
    }

    fun submitJsonList(list: List<String>) {
        val rows = list.map { jsonString ->
            var obj: JsonObject? = null
            try {
                obj = JsonUtils.gson.fromJson(jsonString, JsonObject::class.java)
            } catch (e: Exception) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementsViewHolder {
        val binding = RowOtherInfoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AchievementsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AchievementsViewHolder, position: Int) {
        val row = getItem(position)
        holder.binding.tvRefName.text = row.name
        holder.binding.tvRefRelationship.text = row.relationship
        holder.binding.tvRefPhone.text = row.phone
        holder.binding.tvRefEmail.text = row.email
    }

    class AchievementsViewHolder(val binding: RowOtherInfoBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF_CALLBACK = DiffUtils.itemCallback<ReferenceRow>(
            { oldItem, newItem -> oldItem == newItem },
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
