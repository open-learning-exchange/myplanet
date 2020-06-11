package org.ole.planet.myplanet.ui.myhealth

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import androidx.fragment.app.Fragment
import io.realm.Realm
import org.ole.planet.myplanet.model.RealmUserModel


class UserListArrayAdapter(activity: Activity, val view: Int, var list: List<RealmUserModel>) : ArrayAdapter<RealmUserModel>(activity, view, list) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var v = convertView
        if (v == null) {
            v = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false)
        }
        var tv = v?.findViewById<TextView>(android.R.id.text1)
        var um = getItem(position)
        tv?.text = """${um.fullName} (${um.name})"""

        return v!!;
    }

//    override fun getFilter(): Filter {
//        return myFilter
//    }

//    var myFilter: Filter = object : Filter() {
//        override fun performFiltering(chars: CharSequence): FilterResults {
//            val filterSeq: String = chars.toString().toLowerCase()
//            val result = FilterResults()
//            if (filterSeq.isNotEmpty()) {
//                val filter: ArrayList<RealmUserModel> = ArrayList()
//                activity.runOnUiThread {
//                    for (model in list) {
//                        if (model.firstName.toLowerCase().contains(filterSeq)) filter.add(model)
//                    }
//                }
//                result.count = filter.size
//                result.values = filter
//            } else {
//                synchronized(this) {
//                    result.values = list
//                    result.count = list.size
//                }
//            }
//            return result
//        }
//
//        override fun publishResults(contraint: CharSequence, results: FilterResults) {
//            if (results.values != null) {
//
//                val filtered: ArrayList<RealmUserModel> = results.values as ArrayList<RealmUserModel>
//                notifyDataSetChanged()
////                list =  ArrayList<RealmUserModel>()
//                var i = 0
//                val l = filtered.size
//                while (i < l) {
//                    filtered[i].realm.executeTransaction {
//                        filtered[i].realm.copyToRealm(list)
//                    }
////                    add(filtered[i])
//                    i++
//                }
//            }
//            notifyDataSetInvalidated()
//        }
//    }
}