package org.ole.planet.myplanet.ui.course

import android.content.Context
import android.content.Intent
import androidx.recyclerview.widget.RecyclerView

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.realm.Realm
import kotlinx.android.synthetic.main.row_my_progress.view.*

import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.utilities.TimeUtils

class AdapterMyProgress(private val context: Context, private val realm: Realm, private val list: List<RealmSubmission>, private val examMap : HashMap<String, RealmStepExam>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(context).inflate(R.layout.row_my_progress, parent, false)
        return ViewHolderMyProgress(v)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ViewHolderMyProgress) {
            if (examMap.containsKey(list[position].parentId)){
                var exam = examMap[list[position].parentId]
                var course = realm.where(RealmMyCourse::class.java).equalTo("courseId",exam!!.courseId).findFirst()
                holder.tvTitle.text = course!!.courseTitle
                var answers = realm.where(RealmAnswer::class.java).equalTo("examId", examMap[list[position].parentId]!!.id).findAll()
                var totalMistakes = 0;
                answers.map {
                    totalMistakes += it.mistakes
                }
                holder.tv_total.text =  "${totalMistakes}"
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    internal inner class ViewHolderMyProgress(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvTitle: TextView = itemView.tv_title
        var tv_total: TextView = itemView.tv_total

    }
}
