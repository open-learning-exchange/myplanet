package org.ole.planet.myplanet.ui.references

import android.content.Context
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

class ReferencesAdapter(private val context: Context, list: List<String>) :
    ListAdapter<String, ReferencesViewHolder>(DIFF_CALLBACK) {

    init {
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReferencesViewHolder {
        val binding = RowOtherInfoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ReferencesViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReferencesViewHolder, position: Int) {
        val jsonString = getItem(position)
        val `object` = JsonUtils.gson.fromJson(jsonString, JsonObject::class.java)
        val res = """
            ${getString("name", `object`)}
            ${getString("relationship", `object`)}
            ${getString("phone", `object`)}
            ${getString("email", `object`)}
            """.trimIndent()
        holder.rowOtherInfoBinding.tvDescription.text = res
    }

    class ReferencesViewHolder(var rowOtherInfoBinding: RowOtherInfoBinding) : RecyclerView.ViewHolder(rowOtherInfoBinding.root)

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<String>(
            { oldItem, newItem -> oldItem == newItem },
            { oldItem, newItem -> oldItem == newItem }
        )
    }
}
