package org.ole.planet.myplanet.ui.survey

import androidx.recyclerview.widget.DiffUtil
import org.ole.planet.myplanet.model.RealmStepExam

class SurveyDiffCallback(
    private val oldList: List<RealmStepExam>,
    private val newList: List<RealmStepExam>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

