package org.ole.planet.myplanet.ui.dashboard.notification

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.databinding.FragmentNotificationBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Notifications
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.FileUtils

class NotificationFragment : BottomSheetDialogFragment() {
    private lateinit var fragmentNotificationBinding: FragmentNotificationBinding
    lateinit var callback: NotificationCallback
    lateinit var resourceList: List<RealmMyLibrary>
    private lateinit var mRealm: Realm
    private lateinit var notificationList: MutableList<Notifications>
    private lateinit var notificationsAdapter: AdapterNotification

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        callback = object : NotificationCallback {
            override fun showPendingSurveyDialog() {}
            override fun showTaskListDialog() {}
            override fun showUserResourceDialog() {}
            override fun showResourceDownloadDialog() {}
            override fun syncKeyId() {}
            override fun forceDownloadNewsImages() {}
            override fun downloadDictionary() {}
        }
        mRealm = DatabaseService(requireActivity()).realmInstance
        resourceList = emptyList()
        fragmentNotificationBinding = FragmentNotificationBinding.inflate(inflater, container, false)
        return fragmentNotificationBinding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { d ->
            val dialog = d as BottomSheetDialog
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isHideable = true
            }
        }
        return bottomSheetDialog
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val model = UserProfileDbHandler(requireContext()).userModel!!
        val surveyList = mRealm.where(RealmSubmission::class.java).equalTo("userId", model.id).equalTo("status", "pending").equalTo("type", "survey").findAll()

        val tasks = mRealm.where(RealmTeamTask::class.java).notEqualTo("status", "archived").equalTo("completed", false).equalTo("assignee", model.id).findAll()

//        val notificationList: MutableList<Notifications> = ArrayList()
//
//        val resourceCount = getLibraryList(mRealm, model.id).size
//        notificationList.add(Notifications(R.drawable.mylibrary, "$resourceCount ${getString(R.string.resource_not_downloaded)}. ${getString(R.string.bulk_resource_download)}"))
//        notificationList.add(Notifications(R.drawable.survey, "${surveyList.size} ${getString(R.string.pending_survey)} / ${tasks.size} ${getString(R.string.tasks_due)}"))

        val storageRatio = FileUtils.totalAvailableMemoryRatio
        val storageNotiText: String = if (storageRatio <= 10) {
            "${getString(R.string.storage_critically_low)} $storageRatio% ${getString(R.string.available_please_free_up_space)}"
        } else if (storageRatio <= 40) {
            "${getString(R.string.storage_running_low)} $storageRatio% ${getString(R.string.available)}"
        } else {
            "${getString(R.string.storage_available)} $storageRatio%."
        }
//        notificationList.add(Notifications(R.drawable.baseline_storage_24, storageNotiText))
//
//        if (!TextUtils.isEmpty(model.key) && model.getRoleAsString().contains("health") && !model.id?.startsWith("guest")!!) {
//            notificationList.add(Notifications(R.drawable.ic_myhealth, getString(R.string.health_record_not_available_click_to_sync)))
//        }
//
//        fragmentNotificationBinding.rvNotifications.layoutManager = LinearLayoutManager(requireActivity())
//        fragmentNotificationBinding.rvNotifications.adapter = AdapterNotification(requireActivity(), notificationList, callback)
        fragmentNotificationBinding.icBack.setOnClickListener {
            dismiss()
        }
    }
}
