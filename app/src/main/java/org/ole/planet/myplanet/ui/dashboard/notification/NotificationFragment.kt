package org.ole.planet.myplanet.ui.dashboard.notification

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_notification.ic_back
import kotlinx.android.synthetic.main.fragment_notification.rv_notifications
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Notifications
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.FileUtils.getTotalAvailableMemoryRatio
import java.util.Calendar

class NotificationFragment : BottomSheetDialogFragment() {
    public lateinit var callback: NotificationCallback
    public lateinit var resourceList: List<RealmMyLibrary>
    public var mRealm: Realm? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        mRealm = DatabaseService(requireActivity()).realmInstance
        resourceList = emptyList()
        return inflater.inflate(R.layout.fragment_notification, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { d ->
            val dialog = d as BottomSheetDialog
            val bottomSheet =
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            BottomSheetBehavior.from(bottomSheet!!).state = BottomSheetBehavior.STATE_EXPANDED
            BottomSheetBehavior.from(bottomSheet).skipCollapsed = true
            BottomSheetBehavior.from(bottomSheet).setHideable(true)
        }
        return bottomSheetDialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var model = UserProfileDbHandler(activity).userModel
        val surveyList = mRealm!!.where(RealmSubmission::class.java).equalTo("userId", model.id)
            .equalTo("status", "pending").equalTo("type", "survey").findAll()
        ic_back.setOnClickListener {
            dismiss()
        }
        val tasks: List<RealmTeamTask> =
            mRealm!!.where(RealmTeamTask::class.java).equalTo("assignee", model.id)
                .equalTo("completed", false)
                .greaterThan("deadline", Calendar.getInstance().timeInMillis).findAll()
        var notificationList: MutableList<Notifications> = ArrayList()
        notificationList.add(
            Notifications(
                R.drawable.mylibrary, "${resourceList.size} ${getString(R.string.resource_not_downloaded)}"
            )
        )
        notificationList.add(Notifications(R.drawable.mylibrary, getString(R.string.bulk_resource_download)))
        notificationList.add(Notifications(R.drawable.survey, "${surveyList.size} ${getString(R.string.pending_survey)}"))
        notificationList.add(Notifications(R.drawable.ic_news, getString(R.string.download_news_images)))
        notificationList.add(Notifications(R.drawable.ic_dictionary, getString(R.string.download_dictionary)))
        notificationList.add(Notifications(R.drawable.task_pending, "${tasks.size} ${getString(R.string.tasks_due)}"))

        var storageRatio = getTotalAvailableMemoryRatio()
        var storageNotiText: String
        if (storageRatio <= 10) {
            storageNotiText =
                "${getString(R.string.storage_critically_low)} $storageRatio% ${getString(R.string.available_please_free_up_space)}"
        } else if (storageRatio <= 40) {
            storageNotiText = "${getString(R.string.storage_running_low)} $storageRatio% ${getString(R.string.available)}"
        } else {
            storageNotiText = "${getString(R.string.storage_available)} $storageRatio%."
        }
        notificationList.add(Notifications(R.drawable.baseline_storage_24, storageNotiText))

        if (TextUtils.isEmpty(model.key) || model.roleAsString.contains("health")) {
            if (!model.id.startsWith("guest")) {
                notificationList.add(
                    Notifications(
                        R.drawable.ic_myhealth, getString(R.string.health_record_not_available_click_to_sync)
                    )
                )
            }
        }

        rv_notifications.layoutManager = LinearLayoutManager(requireActivity())
        rv_notifications.adapter =
            AdapterNotification(requireActivity(), notificationList, callback)
    }
}
