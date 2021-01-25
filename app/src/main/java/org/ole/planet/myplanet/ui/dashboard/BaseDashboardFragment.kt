package org.ole.planet.myplanet.ui.dashboard

import android.app.DatePickerDialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.realm.Case
import io.realm.RealmObject
import io.realm.Sort
import kotlinx.android.synthetic.main.alert_health_list.view.*
import kotlinx.android.synthetic.main.card_profile_bell.view.*
import kotlinx.android.synthetic.main.fragment_home_bell.view.*
import kotlinx.android.synthetic.main.home_card_courses.view.*
import kotlinx.android.synthetic.main.home_card_library.view.*
import kotlinx.android.synthetic.main.home_card_meetups.view.*
import kotlinx.android.synthetic.main.home_card_teams.view.*
import kotlinx.android.synthetic.main.item_library_home.view.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.TransactionSyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationFragment
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.myhealth.UserListArrayAdapter
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.ui.userprofile.UserProfileFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.util.*

open class BaseDashboardFragment : BaseDashboardFragmentPlugin(), NotificationCallback, SyncListener {
    var fullName: String? = null
    var dbService: DatabaseService? = null
    var params = LinearLayout.LayoutParams(250, 100)
    var di: ProgressDialog? = null

    fun onLoaded(v: View) {
        profileDbHandler = UserProfileDbHandler(activity)
        model = profileDbHandler.userModel
        fullName = profileDbHandler.userModel.fullName
        if (fullName?.trim().isNullOrBlank()) {
            fullName = profileDbHandler.userModel.name
            v.ll_prompt.visibility = View.VISIBLE
            v.ll_prompt.setOnClickListener {
                UserInformationFragment.getInstance("").show(childFragmentManager, "")
            }
        } else {
            v.ll_prompt.visibility = View.GONE
        }
        v.ic_close.setOnClickListener {
            v.ll_prompt.visibility = View.GONE
        }
        val imageView = v.findViewById<ImageView>(R.id.imageView)
        if (!TextUtils.isEmpty(model.userImage)) Picasso.get().load(model.userImage).placeholder(R.drawable.profile).into(imageView, object : Callback {
            override fun onSuccess() {}
            override fun onError(e: Exception) {
                e.printStackTrace()
                val f = File(model.userImage)
                Picasso.get().load(f).placeholder(R.drawable.profile).error(R.drawable.profile).into(imageView)
            }
        })
        v.txtVisits.text = """${profileDbHandler.offlineVisits} visits"""
        v.txtRole.text = """ - ${model.roleAsString}"""
        v.txtFullName.text = fullName
    }

    override fun forceDownloadNewsImages() {
        if (mRealm == null) mRealm = DatabaseService(activity).realmInstance
        Utilities.toast(activity, "Please select starting date : ")
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(activity!!, DatePickerDialog.OnDateSetListener { _: DatePicker?, i: Int, i1: Int, i2: Int ->
            now[Calendar.YEAR] = i
            now[Calendar.MONTH] = i1
            now[Calendar.DAY_OF_MONTH] = i2
            val imageList: List<RealmMyLibrary> = mRealm.where(RealmMyLibrary::class.java).equalTo("isPrivate", true).greaterThan("createdDate", now.timeInMillis).equalTo("mediaType", "image").findAll()
            val urls = ArrayList<String>()
            getUrlsAndStartDownload(imageList, BaseResourceFragment.settings, urls as ArrayList<String?>)
        }, now[Calendar.YEAR],
                now[Calendar.MONTH],
                now[Calendar.DAY_OF_MONTH])
        dpd.setTitle("Read offline news from : ")
        dpd.show()
    }

    override fun downloadDictionary() {
        val list = ArrayList<String>()
        list.add(Constants.DICTIONARY_URL)
        if (!FileUtils.checkFileExist(Constants.DICTIONARY_URL)) {
            Utilities.toast(activity, "Downloading started, please check notification...")
            Utilities.openDownloadService(activity, list)
        } else {
            Utilities.toast(activity, "File already exists...")
        }
    }

    private fun myLibraryDiv(view: View) {
        view.flexboxLayout.flexDirection = FlexDirection.ROW
        val dbMylibrary = RealmMyLibrary.getMyLibraryByUserId(mRealm, BaseResourceFragment.settings)
        if (dbMylibrary.size == 0) {
            view.count_library.visibility = View.GONE
        } else {
            view.count_library.text = dbMylibrary.size.toString() + ""
        }
        var itemCnt = 0
        for (items in dbMylibrary) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_library_home, null)
            setTextColor(v.findViewById(R.id.title), itemCnt, RealmMyLibrary::class.java)
            v.setBackgroundColor(resources.getColor(if (itemCnt % 2 == 0) R.color.md_white_1000 else R.color.md_grey_300))
            ((v.title) as TextView).text = items.title
            v.detail.setOnClickListener { if (homeItemClickListener != null) homeItemClickListener.openLibraryDetailFragment(items) }
            myLibraryItemClickAction(v.findViewById(R.id.title), items)
            view.flexboxLayout.addView(v, params)
            itemCnt++
        }
    }

    private fun initializeFlexBoxView(v: View, id: Int, c: Class<*>) {
        val flexboxLayout: FlexboxLayout = v.findViewById(id)
        flexboxLayout.flexDirection = FlexDirection.ROW
        setUpMyList(c, flexboxLayout, v)
    }

    private fun setUpMyList(c: Class<*>, flexboxLayout: FlexboxLayout, view: View) {
        val dbMycourses: List<RealmObject>
        val userId = BaseResourceFragment.settings.getString("userId", "--")
        setUpMyLife(userId!!)
        dbMycourses = (if (c == RealmMyCourse::class.java) {
            RealmMyCourse.getMyByUserId(mRealm, BaseResourceFragment.settings)
        } else if (c == RealmMyTeam::class.java) {
            val i = myTeamInit(flexboxLayout)
            setCountText(i, RealmMyTeam::class.java, view)
            return
        } else if (c == RealmMyLife::class.java) {
            myLifeListInit(flexboxLayout)
            return
        } else {
            mRealm.where(c as Class<RealmObject>).contains("userId", userId, Case.INSENSITIVE).findAll()
        }) as List<RealmObject>
        setCountText(dbMycourses.size, c, view)
        val myCoursesTextViewArray = arrayOfNulls<TextView>(dbMycourses.size)
        var itemCnt = 0
        for (items in dbMycourses) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, items, c)
            myCoursesTextViewArray[itemCnt]?.let { setTextColor(it, itemCnt, c) }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params)
            itemCnt++
        }
    }

    private fun myTeamInit(flexboxLayout: FlexboxLayout): Int {
        val dbMyTeam = RealmMyTeam.getMyTeamsByUserId(mRealm, BaseResourceFragment.settings)
        val userId = UserProfileDbHandler(activity).userModel.id
        var count = 0
        for (ob in dbMyTeam) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_home_my_team, flexboxLayout, false)
            val name = v.findViewById<TextView>(R.id.tv_name)
            setBackgroundColor(v, count)
            if ((ob as RealmMyTeam).teamType == "sync") {
                name.setTypeface(null, Typeface.BOLD)
            }
            handleClick(ob._id, ob.name, TeamDetailFragment(), name)
            showNotificationIcons(ob, v, userId)
            name.text = ob.name
            flexboxLayout.addView(v, params)
            count++
        }
        return dbMyTeam.size
    }

    private fun showNotificationIcons(ob: RealmObject, v: View, userId: String) {
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        val imgTask = v.findViewById<ImageView>(R.id.img_task)
        val imgChat = v.findViewById<ImageView>(R.id.img_chat)
        val notification: RealmTeamNotification? = mRealm.where(RealmTeamNotification::class.java)
                .equalTo("parentId", (ob as RealmMyTeam)._id)
                .equalTo("type", "chat")
                .findFirst()
        val chatCount: Long = mRealm.where(RealmNews::class.java).equalTo("viewableBy", "teams").equalTo("viewableId", ob._id).count()
        if (notification != null) {
            imgChat.visibility = if (notification.lastCount < chatCount) View.VISIBLE else View.GONE
        }
        val tasks: List<RealmTeamTask> = mRealm.where(RealmTeamTask::class.java).equalTo("teamId", ob._id).equalTo("completed", false).equalTo("assignee", userId)
                .between("deadline", current, tomorrow.timeInMillis).findAll()
        imgTask.visibility = if (tasks.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun myLifeListInit(flexboxLayout: FlexboxLayout) {
        val dbMylife: MutableList<RealmMyLife>
        val rawMylife: List<RealmMyLife> = RealmMyLife.getMyLifeByUserId(mRealm, BaseResourceFragment.settings)
        dbMylife = ArrayList()
        for (item in rawMylife) if (item.isVisible) dbMylife.add(item)
        var itemCnt = 0
        for (items in dbMylife) {
            flexboxLayout.addView(getLayout(itemCnt, items), params)
            itemCnt++
        }
    }

    private fun setUpMyLife(userId: String) {
        val realm = DatabaseService(context).realmInstance
        val realmObjects = RealmMyLife.getMyLifeByUserId(mRealm, BaseResourceFragment.settings)
        if (realmObjects.isEmpty()) {
            if (!realm.isInTransaction) realm.beginTransaction()
            val myLifeListBase = getMyLifeListBase(userId)
            var ml: RealmMyLife
            var weight = 1
            for (item in myLifeListBase) {
                ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                ml.title = item.title
                ml.imageId = item.imageId
                ml.weight = weight
                ml.userId = item.userId
                ml.isVisible = true
                weight++
            }
            realm.commitTransaction()
        }
    }

    private fun myLibraryItemClickAction(textView: TextView, items: RealmMyLibrary?) {
        textView.setOnClickListener { v: View? -> items?.let {
            openResource(it)
        } }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileDbHandler.onDestory()
    }

    private fun setCountText(countText: Int, c: Class<*>, v: View) {
        if (c == RealmMyCourse::class.java) {
            updateCountText(countText, v.count_course)
        } else if (c == RealmMeetup::class.java) {
            updateCountText(countText, v.count_meetup)
        } else if (c == RealmMyTeam::class.java) {
            updateCountText(countText, v.count_team)
        }
    }

    private fun updateCountText(countText: Int, tv: TextView) {
        tv.text = countText.toString() + ""
        hideCountIfZero(tv, countText)
    }

    private fun hideCountIfZero(v: View, count: Int) {
        v.visibility = if (count == 0) View.GONE else View.VISIBLE
    }

    fun initView(view: View) {
        view.findViewById<View>(R.id.imageView).setOnClickListener { homeItemClickListener.openCallFragment(UserProfileFragment()) }
        view.findViewById<View>(R.id.txtFullName).setOnClickListener { homeItemClickListener.openCallFragment(UserProfileFragment()) }
        dbService = DatabaseService(activity)
        mRealm = dbService?.realmInstance
        myLibraryDiv(view)
        initializeFlexBoxView(view, R.id.flexboxLayoutCourse, RealmMyCourse::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutTeams, RealmMyTeam::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutMeetups, RealmMeetup::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutMyLife, RealmMyLife::class.java)
        showNotificationFragment()
    }

    fun showNotificationFragment() {
        val fragment = NotificationFragment()
        fragment.callback = this
        fragment.resourceList = getLibraryList(mRealm)
        fragment.show(childFragmentManager, "")
    }

    override fun showResourceDownloadDialog() {
        showDownloadDialog(getLibraryList(mRealm))
    }


    override fun showUserResourceDialog() {
        var userModelList: List<RealmUserModel>
        var dialog :AlertDialog? = null;
        userModelList = mRealm!!.where(RealmUserModel::class.java).sort("joinDate", Sort.DESCENDING).findAll()
        var adapter = UserListArrayAdapter(activity!!, android.R.layout.simple_list_item_1, userModelList)
        val alertHealth = LayoutInflater.from(activity).inflate(R.layout.alert_health_list, null)
        val btnAddMember = alertHealth.btn_add_member
        alertHealth.et_search.visibility = View.GONE
        alertHealth.spn_sort.visibility = View.GONE
        btnAddMember.setOnClickListener { startActivity(Intent(requireContext(), BecomeMemberActivity::class.java)) }
        val lv = alertHealth.list
        lv.adapter = adapter
        lv.onItemClickListener = AdapterView.OnItemClickListener { adapterView: AdapterView<*>?, view: View, i: Int, l: Long ->
            val selected = lv.adapter.getItem(i) as RealmUserModel
            Utilities.log("On item selected");
            showDownloadDialog(getLibraryList(mRealm, selected._id))
            dialog?.dismiss()
        }
//        sortList(spnSort, lv);
       dialog =  AlertDialog.Builder(activity!!).setTitle(getString(R.string.select_member)).setView(alertHealth).setCancelable(false).setNegativeButton("Dismiss", null).create()
        dialog?.show()
    }

    override fun syncKeyId() {
        di = ProgressDialog(activity)
        di?.setMessage("Syncing health , please wait...")
        Utilities.log(model.roleAsString)
        if (model.roleAsString.contains("health")) {
            TransactionSyncManager.syncAllHealthData(mRealm, BaseResourceFragment.settings, this)
        } else {
            TransactionSyncManager.syncKeyIv(mRealm, BaseResourceFragment.settings, this)
        }
    }

    override fun onSyncStarted() {
        di?.show()
    }

    override fun onSyncComplete() {
        di?.dismiss()
        Utilities.toast(activity, "myHealth synced successfully")
    }

    override fun onSyncFailed(msg: String) {
        di?.dismiss()
        Utilities.toast(activity, "myHealth synced failed")
    }

    override fun showTaskListDialog() {
        val tasks: List<RealmTeamTask> = mRealm!!.where(RealmTeamTask::class.java).equalTo("assignee", model.id).equalTo("completed", false).greaterThan("deadline", Calendar.getInstance().timeInMillis).findAll()
        if (tasks.size == 0){
            Utilities.toast(requireContext(), "No due tasks")
            return
        }
        var adapter = ArrayAdapter<RealmTeamTask>(requireContext(), android.R.layout.simple_expandable_list_item_1, tasks)
        AlertDialog.Builder(requireContext()).setTitle("Due tasks").setAdapter(adapter, object:DialogInterface.OnClickListener {
            override fun onClick(p0: DialogInterface?, p1: Int) {
                var task = adapter.getItem(p1);
            }
        }).setNegativeButton("Dismiss", null).show();
    }
}