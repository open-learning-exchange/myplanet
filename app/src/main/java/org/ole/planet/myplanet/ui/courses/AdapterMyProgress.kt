package org.ole.planet.myplanet.ui.courses

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonArray
import org.ole.planet.myplanet.databinding.RowMyProgressBinding

class AdapterMyProgress(
    private val context: Context,
    private val list: JsonArray,
) : RecyclerView.Adapter<AdapterMyProgress.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowMyProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val jsonObject = list[position].asJsonObject
        holder.binding.tvTitle.text = jsonObject["courseName"].asString
        val courseId = jsonObject["courseId"].asString
        holder.itemView.setOnClickListener {
            val intent = Intent(context, CourseProgressActivity::class.java)
            intent.putExtra("courseId", courseId)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int {
        return list.size()
    }

    inner class ViewHolder(val binding: RowMyProgressBinding) : RecyclerView.ViewHolder(binding.root)
}
