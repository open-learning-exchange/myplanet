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

    override fun onBindViewHolder(holder: AchievementsViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val row = getItem(position)
        val flatPayloads = payloads.flatMap { if (it is List<*>) it else listOf(it) }
        val payloadSet = flatPayloads.filterIsInstance<String>().toSet()

        // If there are payloads we didn't handle, default to full bind
        val handledPayloads = setOf(PAYLOAD_PHONE, PAYLOAD_EMAIL)
        if (payloadSet.subtract(handledPayloads).isNotEmpty() || flatPayloads.any { it !is String }) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        if (payloadSet.contains(PAYLOAD_PHONE)) {
            holder.binding.tvRefPhone.text = row.phone
        }
        if (payloadSet.contains(PAYLOAD_EMAIL)) {
            holder.binding.tvRefEmail.text = row.email
        }
    }

    class AchievementsViewHolder(val binding: RowOtherInfoBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        const val PAYLOAD_PHONE = "payload_phone"
        const val PAYLOAD_EMAIL = "payload_email"

        private val DIFF_CALLBACK = DiffUtils.itemCallback<ReferenceRow>(
            areItemsTheSame = { oldItem, newItem ->
                // We use name and relationship as the unique identity.
                oldItem.name == newItem.name && oldItem.relationship == newItem.relationship
            },
            areContentsTheSame = { oldItem, newItem -> oldItem == newItem },
            getChangePayload = { oldItem, newItem ->
                val payloads = mutableListOf<String>()
                // Name and relationship are the identity so they shouldn't change for the "same" item
                if (oldItem.phone != newItem.phone) payloads.add(PAYLOAD_PHONE)
                if (oldItem.email != newItem.email) payloads.add(PAYLOAD_EMAIL)
                if (payloads.isNotEmpty()) payloads else null
            }
        )
    }
}

data class ReferenceRow(
    val name: String,
    val relationship: String,
    val phone: String,
    val email: String
)
