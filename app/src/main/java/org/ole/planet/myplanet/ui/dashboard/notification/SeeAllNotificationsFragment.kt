package org.ole.planet.myplanet.ui.dashboard.notification

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentSeeAllNotificationsBinding
import org.ole.planet.myplanet.model.Notifications
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME

class SeeAllNotificationsFragment : Fragment() {
    private lateinit var fragmentSeeAllNotificationsBinding: FragmentSeeAllNotificationsBinding
    lateinit var settings: SharedPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentSeeAllNotificationsBinding = FragmentSeeAllNotificationsBinding.inflate(inflater, container, false)
        settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return fragmentSeeAllNotificationsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentSeeAllNotificationsBinding.recyclerViewNotifications.layoutManager = LinearLayoutManager(context)
        val newNotifications = settings.getString("new_data", null)
        if (newNotifications != null) {
            val notifications: List<Notifications> = parseJson(newNotifications)
            Log.d("Notifications", newNotifications)
            fragmentSeeAllNotificationsBinding.recyclerViewNotifications.adapter = AdapterNotification(notifications)
        }
        val btnMarkAllAsRead: Button = view.findViewById(R.id.btn_mark_all_as_read)
        btnMarkAllAsRead.setOnClickListener {}
    }

    private fun parseJson(json: String): List<Notifications> {
        val gson = Gson()
        val type = object : TypeToken<List<Notifications>>() {}.type
        return gson.fromJson(json, type)
    }
}
