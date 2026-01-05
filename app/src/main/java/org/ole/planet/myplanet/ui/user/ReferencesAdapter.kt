package org.ole.planet.myplanet.ui.user

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import org.ole.planet.myplanet.databinding.RowOtherInfoBinding
import org.ole.planet.myplanet.ui.user.ReferencesAdapter.ViewHolderOtherInfo
import org.ole.planet.myplanet.utilities.DiffUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString

class ReferencesAdapter(private val context: Context, list: List<String>) :
    ListAdapter<String, ViewHolderOtherInfo>(DIFF_CALLBACK) {

    init {
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderOtherInfo {
        val binding = RowOtherInfoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolderOtherInfo(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderOtherInfo, position: Int) {
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

    class ViewHolderOtherInfo(var rowOtherInfoBinding: RowOtherInfoBinding) : RecyclerView.ViewHolder(rowOtherInfoBinding.root)

    companion object {
        val DIFF_CALLBACK = DiffUtils.itemCallback<String>(
            { oldItem, newItem -> oldItem == newItem },
            { oldItem, newItem -> oldItem == newItem }
        )
    }
}
