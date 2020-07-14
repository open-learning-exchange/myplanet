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
import org.ole.planet.myplanet.service.UserProfileDbHandler

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
        val surveyList = mRealm!!.where(RealmSubmission::class.java).equalTo("userId", model.getId()).equalTo("status", "pending").equalTo("type", "survey").findAll()
        ic_back.setOnClickListener {
            dismiss()
        }
        var notificationList: MutableList<Notifications> = ArrayList()
        notificationList.add(Notifications(R.drawable.mylibrary, "Download user resources."))
        notificationList.add(Notifications(R.drawable.mylibrary, "${resourceList.size} resource not downloaded."))
        notificationList.add(Notifications(R.drawable.survey, "${surveyList.size} pending survey."))
        notificationList.add(Notifications(R.drawable.ic_news, "Download news images."))
        notificationList.add(Notifications(R.drawable.ic_dictionary, "Download dictionary."))


        if (TextUtils.isEmpty(model.key) || model.roleAsString.contains("health")) {
            notificationList.add(Notifications(R.drawable.ic_myhealth, "Health record not available. Click to sync."))
        }

        rv_notifications.layoutManager = LinearLayoutManager(activity!!)
        rv_notifications.adapter = AdapterNotification(activity!!, notificationList, callback)
    }

}
