package org.ole.planet.myplanet.ui.dashboard

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Case
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentNotificationsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.utilities.FileUtils.totalAvailableMemoryRatio
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate

class NotificationsFragment : Fragment() {
    private lateinit var fragmentNotificationsBinding: FragmentNotificationsBinding
    private lateinit var databaseService: DatabaseService
    private lateinit var mRealm: Realm

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentNotificationsBinding = FragmentNotificationsBinding.inflate(inflater, container, false)
        databaseService = DatabaseService(requireActivity())
        mRealm = databaseService.realmInstance

        val spinnerAdapter = ArrayAdapter.createFromResource(requireContext(), R.array.status_options, android.R.layout.simple_spinner_item)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fragmentNotificationsBinding.status.adapter = spinnerAdapter

        fragmentNotificationsBinding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = "${parent.getItemAtPosition(position)}"
                Log.d("NotificationsFragment", "Selected option: $selectedOption")
                // Handle selection here
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle case where nothing is selected
            }
        }

        val userId = arguments?.getString("userId") ?: ""
        val resourceCount = arguments?.getInt("resourceCount") ?: 0

        val pendingSurveys = getPendingSurveys(userId, mRealm)
        val surveyTitles = getSurveyTitlesFromSubmissions(pendingSurveys, mRealm)

        val tasks = mRealm.where(RealmTeamTask::class.java)
            .notEqualTo("status", "archived")
            .equalTo("completed", false)
            .equalTo("assignee", userId)
            .findAll()

        val storageRatio = totalAvailableMemoryRatio
        val storageNotificationText = when {
            storageRatio <= 10 -> {
                "${getString(R.string.storage_critically_low)} $storageRatio% ${getString(R.string.available_please_free_up_space)}"
            }
            storageRatio <= 40 -> {
                "${getString(R.string.storage_running_low)} $storageRatio% ${getString(R.string.available)}"
            }
            else -> null
        }

        val notificationList = mutableListOf<SpannableString>()
        notificationList.add(createBoldNotification("you have $resourceCount resources not downloaded", "$resourceCount"))
        surveyTitles.forEach { title ->
            notificationList.add(createBoldNotification("you have a pending survey $title", title))
        }
        tasks.forEach { notificationList.add(createBoldNotification("${it.title} is due in ${formatDate(it.deadline)}", "${it.title}")) }
        storageNotificationText?.let { notificationList.add(createBoldNotification(it, it)) }
        if (notificationList.size < 1) {
            fragmentNotificationsBinding.emptyData.visibility = View.VISIBLE
        }

        val adapter = AdapterNotifications(notificationList)
        fragmentNotificationsBinding.rvNotifications.adapter = adapter
        fragmentNotificationsBinding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        return fragmentNotificationsBinding.root
    }

    private fun getPendingSurveys(userId: String?, realm: Realm): List<RealmSubmission> {
        return realm.where(RealmSubmission::class.java)
            .equalTo("userId", userId)
            .equalTo("type", "survey")
            .equalTo("status", "pending", Case.INSENSITIVE)
            .findAll()
    }

    private fun getSurveyTitlesFromSubmissions(submissions: List<RealmSubmission>, realm: Realm): List<String> {
        val titles = mutableListOf<String>()
        submissions.forEach { submission ->
            val exam = realm.where(RealmStepExam::class.java)
                .equalTo("id", submission.parentId)
                .findFirst()
            exam?.name?.let { titles.add(it) }
        }
        return titles
    }

    private fun createBoldNotification(message: String, boldText: String): SpannableString {
        val spannableString = SpannableString(message)
        val start = message.indexOf(boldText)
        val end = start + boldText.length
        spannableString.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
    }

    override fun onDestroy() {
        super.onDestroy()
        mRealm.close()
    }
}