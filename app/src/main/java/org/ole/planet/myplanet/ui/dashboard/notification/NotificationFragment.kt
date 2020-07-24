package org.ole.planet.myplanet.ui.dashboard.notification


import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_notification.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Notifications
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.FileUtils.getTotalAvailableMemoryRatio
import java.util.*
import kotlin.collections.ArrayList

/**
 * A simple [Fragment] subclass.
 */
class NotificationFragment : BottomSheetDialogFragment() {

    public lateinit var callback: NotificationCallback
    public lateinit var resourceList: List<RealmMyLibrary>
    public var mRealm: Realm? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        mRealm = DatabaseService(activity!!).realmInstance
        return inflater.inflate(R.layout.fragment_notification, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { d ->
            val dialog = d as BottomSheetDialog
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            BottomSheetBehavior.from(bottomSheet!!).state = BottomSheetBehavior.STATE_EXPANDED
            BottomSheetBehavior.from(bottomSheet).skipCollapsed = true
            BottomSheetBehavior.from(bottomSheet).setHideable(true)
        }
        return bottomSheetDialog
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        var model = UserProfileDbHandler(activity).userModel
        val surveyList = mRealm!!.where(RealmSubmission::class.java).equalTo("userId", model.id).equalTo("status", "pending").equalTo("type", "survey").findAll()
        ic_back.setOnClickListener {
            dismiss()
        }
        val tasks: List<RealmTeamTask> = mRealm!!.where(RealmTeamTask::class.java).equalTo("assignee", model.id).equalTo("completed", false).greaterThan("deadline", Calendar.getInstance().timeInMillis).findAll()
        var notificationList: MutableList<Notifications> = ArrayList()
        notificationList.add(Notifications(R.drawable.mylibrary, "${resourceList.size} resource not downloaded."))
        notificationList.add(Notifications(R.drawable.mylibrary, "Bulk resource download."))
        notificationList.add(Notifications(R.drawable.survey, "${surveyList.size} pending survey."))
        notificationList.add(Notifications(R.drawable.ic_news, "Download news images."))
        notificationList.add(Notifications(R.drawable.ic_dictionary, "Download dictionary."))
        notificationList.add(Notifications(R.drawable.task_pending,  "${tasks.size} tasks due."))

        var storageRatio = getTotalAvailableMemoryRatio()
        var storageNotiText : String
        if (storageRatio <= 10) {
            storageNotiText = "Storage critically low: " + storageRatio + "% available. Please free up space."
        }
        else if (storageRatio <= 40) {
            storageNotiText = "Storage running low: " + storageRatio + "% available."
        }
        else {
            storageNotiText = "Storage available: " + storageRatio + "%."
        }
        notificationList.add(Notifications(R.drawable.baseline_storage_24, storageNotiText))

        if (TextUtils.isEmpty(model.key) || model.roleAsString.contains("health")) {
            if (!model.id.startsWith("guest")) {
                notificationList.add(Notifications(R.drawable.ic_myhealth, "Health record not available. Click to sync."))
            }
        }

        rv_notifications.layoutManager = LinearLayoutManager(activity!!)
        rv_notifications.adapter = AdapterNotification(activity!!, notificationList, callback)
    }

}
