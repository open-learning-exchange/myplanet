package org.ole.planet.myplanet.ui.userprofile

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.RealmList
import org.ole.planet.myplanet.databinding.RowOtherInfoBinding
import org.ole.planet.myplanet.ui.userprofile.AdapterOtherInfo.ViewHolderOtherInfo
import org.ole.planet.myplanet.utilities.JsonUtils.getString

class AdapterOtherInfo(private val context: Context, private val list: RealmList<String>) :
    RecyclerView.Adapter<ViewHolderOtherInfo>() {
    private lateinit var rowOtherInfoBinding: RowOtherInfoBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderOtherInfo {
        rowOtherInfoBinding = RowOtherInfoBinding.inflate(
            LayoutInflater.from(context), parent, false
        )
        return ViewHolderOtherInfo(rowOtherInfoBinding)
    }

    override fun onBindViewHolder(holder: ViewHolderOtherInfo, position: Int) {
        if (position < list.size) {
            val jsonString = list[position]
            val `object` = Gson().fromJson(jsonString, JsonObject::class.java)
            val res = """
                ${getString("name", `object`)}
                ${getString("relationship", `object`)}
                ${getString("phone", `object`)}
                ${getString("email", `object`)}
                """.trimIndent()
            holder.rowOtherInfoBinding.tvDescription.text = res
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    class ViewHolderOtherInfo(var rowOtherInfoBinding: RowOtherInfoBinding) : RecyclerView.ViewHolder(rowOtherInfoBinding.root)
}
