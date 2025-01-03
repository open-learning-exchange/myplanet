package org.ole.planet.myplanet.ui.dashboard

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.flexbox.*
import io.realm.kotlin.query.*
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.*
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.*
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.myhealth.UserListArrayAdapter
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.userprofile.*
import org.ole.planet.myplanet.utilities.*
import java.util.*

open class BaseDashboardFragment : BaseDashboardFragmentPlugin(), NotificationCallback,
    SyncListener {
    private var fullName: String? = null
    lateinit var dbService: DatabaseService
    private var params = LinearLayout.LayoutParams(250, 100)
    private var di: DialogUtils.CustomProgressDialog? = null
    private lateinit var myCoursesResults: RealmResults<RealmMyCourse>
    private lateinit var myTeamsResults: RealmResults<RealmMyTeam>
    private lateinit var offlineActivitiesResults: RealmResults<RealmOfflineActivity>
    private val scope = MainApplication.applicationScope

    fun onLoaded(v: View) {
        profileDbHandler = UserProfileDbHandler(requireContext())
        model = profileDbHandler.userModel
        fullName = profileDbHandler.userModel?.getFullName()
        if (fullName?.trim().isNullOrBlank()) {
            fullName = profileDbHandler.userModel?.name
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.VISIBLE
            v.findViewById<LinearLayout>(R.id.ll_prompt).setOnClickListener {
                if (!childFragmentManager.isStateSaved) {
                    UserInformationFragment.getInstance("").show(childFragmentManager, "")
                }
            }
        } else {
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.GONE
        }
        v.findViewById<ImageView>(R.id.ic_close).setOnClickListener {
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.GONE
        }
        val imageView = v.findViewById<ImageView>(R.id.imageView)
        if (!TextUtils.isEmpty(model?.userImage)) {
            Glide.with(requireActivity())
                .load(model?.userImage)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.profile)
        }

        offlineActivitiesResults = mRealm.query<RealmOfflineActivity>(RealmOfflineActivity::class,
            "userName == $0 AND type == $1", profileDbHandler.userModel?.name, UserProfileDbHandler.KEY_LOGIN).find()

        v.findViewById<TextView>(R.id.txtRole).text = getString(R.string.user_role, model?.getRoleAsString())
        v.findViewById<TextView>(R.id.txtFullName).text = getString(R.string.user_name, fullName, profileDbHandler.offlineVisits)
    }

    override fun forceDownloadNewsImages() {
        mRealm = DatabaseService().realmInstance
        Utilities.toast(activity, getString(R.string.please_select_starting_date))
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(requireActivity(), { _: DatePicker?, i: Int, i1: Int, i2: Int ->
            now[Calendar.YEAR] = i
            now[Calendar.MONTH] = i1
            now[Calendar.DAY_OF_MONTH] = i2
            val imageList = mRealm.query<RealmMyLibrary>(RealmMyLibrary::class, "isPrivate == true AND createdDate > $0 AND mediaType == $1", now.timeInMillis, "image").find()
            val urls = ArrayList<String>()
            getUrlsAndStartDownload(imageList, urls) },
            now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH]
        )
        dpd.setTitle(getString(R.string.read_offline_news_from))
        dpd.show()
    }

    override fun downloadDictionary() {
        val list = ArrayList<String>()
        list.add(Constants.DICTIONARY_URL)
        if (!FileUtils.checkFileExist(Constants.DICTIONARY_URL)) {
            Utilities.toast(activity, getString(R.string.downloading_started_please_check_notification))
            Utilities.openDownloadService(activity, list, false)
        } else {
            Utilities.toast(activity, getString(R.string.file_already_exists))
        }
    }

    private fun myLibraryDiv(view: View) {
        view.findViewById<FlexboxLayout>(R.id.flexboxLayout).flexDirection = FlexDirection.ROW
        val dbMylibrary = RealmMyLibrary.getMyLibraryByUserId(mRealm, settings)
        if (dbMylibrary.isEmpty()) {
            view.findViewById<TextView>(R.id.count_library).visibility = View.GONE
        } else {
            view.findViewById<TextView>(R.id.count_library).text = getString(R.string.number_placeholder, dbMylibrary.size)
        }
        for ((itemCnt, items) in dbMylibrary.withIndex()) {
            val itemLibraryHomeBinding = ItemLibraryHomeBinding.inflate(LayoutInflater.from(activity))
            val v = itemLibraryHomeBinding.root
            setTextColor(itemLibraryHomeBinding.title, itemCnt)
            val colorResId = if (itemCnt % 2 == 0) R.color.dashboard_item else R.color.dashboard_item_alternative
            val color = context?.let { ContextCompat.getColor(it, colorResId) }
            if (color != null) {
                v.setBackgroundColor(color)
            }

            itemLibraryHomeBinding.title.text = items.title
            itemLibraryHomeBinding.detail.setOnClickListener {
                if (homeItemClickListener != null) {
                    homeItemClickListener?.openLibraryDetailFragment(items)
                }
            }

            myLibraryItemClickAction(itemLibraryHomeBinding.title, items)
            view.findViewById<FlexboxLayout>(R.id.flexboxLayout).addView(v, params)
        }
    }

    private fun initializeFlexBoxView(v: View, id: Int, c: Class<out RealmObject>) {
        val flexboxLayout: FlexboxLayout = v.findViewById(id)
        flexboxLayout.flexDirection = FlexDirection.ROW
        setUpMyList(c, flexboxLayout, v)
    }

    private fun setUpMyList(c: Class<out RealmObject>, flexboxLayout: FlexboxLayout, view: View) {
        val dbMycourses: List<RealmObject>
        val userId = settings?.getString("userId", "--")
        setUpMyLife(userId)
        dbMycourses = when (c) {
            RealmMyCourse::class.java -> {
                RealmMyCourse.getMyByUserId(mRealm, settings)
            }
            RealmMyTeam::class.java -> {
                val i = myTeamInit(flexboxLayout)
                setCountText(i, RealmMyTeam::class.java, view)
                return
            }
            RealmMyLife::class.java -> {
                myLifeListInit(flexboxLayout)
                return
            }
            else -> {
                userId?.let {
                    mRealm.query<RealmObject>(RealmObject::class).find()
                } ?: listOf()
            }
        }
        setCountText(dbMycourses.size, c, view)
        val myCoursesTextViewArray = arrayOfNulls<TextView>(dbMycourses.size)
        for ((itemCnt, items) in dbMycourses.withIndex()) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, items)
            myCoursesTextViewArray[itemCnt]?.let { setTextColor(it, itemCnt) }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params)
        }
    }

    private fun myTeamInit(flexboxLayout: FlexboxLayout): Int {
        val dbMyTeam = RealmMyTeam.getMyTeamsByUserId(mRealm, settings)
        val userId = UserProfileDbHandler(requireContext()).userModel?.id
        for ((count, ob) in dbMyTeam.withIndex()) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_home_my_team, flexboxLayout, false)
            val name = v.findViewById<TextView>(R.id.tv_name)
            setBackgroundColor(v, count)
            if (ob.teamType == "sync") {
                name.setTypeface(null, Typeface.BOLD)
            }
            handleClick(ob._id, ob.name, TeamDetailFragment(), name)
            showNotificationIcons(ob, v, userId)
            name.text = ob.name
            flexboxLayout.addView(v, params)
        }
        return dbMyTeam.size
    }

    private fun showNotificationIcons(ob: RealmObject, v: View, userId: String?) {
        val current = Calendar.getInstance().timeInMillis
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        val imgTask = v.findViewById<ImageView>(R.id.img_task)
        val imgChat = v.findViewById<ImageView>(R.id.img_chat)
        val notification = mRealm.query<RealmTeamNotification>(RealmTeamNotification::class, "parentId == $0 AND type == $1", (ob as RealmMyTeam)._id, "chat").first().find()

        val chatCount = mRealm.query<RealmNews>(RealmNews::class, "viewableBy == $0 AND viewableId == $1", "teams", ob._id).count().find()

        if (notification != null) {
            imgChat.visibility = if (notification.lastCount < chatCount) View.VISIBLE else View.GONE
        }

        val tasks = mRealm.query<RealmTeamTask>(RealmTeamTask::class, "assignee == $0 AND deadline BETWEEN {$1, $2}", userId, current, tomorrow.timeInMillis).find()
        imgTask.visibility = if (tasks.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun myLifeListInit(flexboxLayout: FlexboxLayout) {
        val dbMylife: MutableList<RealmMyLife> = ArrayList()
        val rawMylife: List<RealmMyLife> = RealmMyLife.getMyLifeByUserId(mRealm, settings)
        for (item in rawMylife) if (item.isVisible) dbMylife.add(item)
        for ((itemCnt, items) in dbMylife.withIndex()) {
            flexboxLayout.addView(getLayout(itemCnt, items), params)
        }
    }

    private fun setUpMyLife(userId: String?) {
        val realm = dbService.realmInstance
        val realmObjects = realm.query<RealmMyLife>(RealmMyLife::class).find()

        if (realmObjects.isEmpty()) {
            realm.writeBlocking {
                val myLifeListBase = getMyLifeListBase(userId)
                var weight = 1
                for (item in myLifeListBase) {
                    copyToRealm(RealmMyLife().apply {
                        _id = UUID.randomUUID().toString()
                        title = item.title
                        imageId = item.imageId
                        this.weight = weight
                        this.userId = item.userId
                        isVisible = true
                    })
                    weight++
                }
            }
        }
    }

    private fun myLibraryItemClickAction(textView: TextView, items: RealmMyLibrary?) {
        textView.setOnClickListener {
            items?.let {
                openResource(it)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        profileDbHandler.onDestroy()
        mRealm.close()
    }

    private fun setCountText(countText: Int, c: Class<*>, v: View) {
        when (c) {
            RealmMyCourse::class.java -> {
                updateCountText(countText, v.findViewById(R.id.count_course))
            }
            RealmMeetup::class.java -> {
                updateCountText(countText, v.findViewById(R.id.count_meetup))
            }
            RealmMyTeam::class.java -> {
                updateCountText(countText, v.findViewById(R.id.count_team))
            }
        }
    }

    private fun updateCountText(countText: Int, tv: TextView) {
        tv.text = getString(R.string.number_placeholder, countText)
        hideCountIfZero(tv, countText)
    }

    private fun hideCountIfZero(v: View, count: Int) {
        v.visibility = if (count == 0) View.GONE else View.VISIBLE
    }

    fun initView(view: View) {
        view.findViewById<View>(R.id.imageView).setOnClickListener {
            homeItemClickListener?.openCallFragment(UserProfileFragment())
        }
        view.findViewById<View>(R.id.txtFullName).setOnClickListener {
            homeItemClickListener?.openCallFragment(UserProfileFragment())
        }
        dbService = DatabaseService()
        mRealm = dbService.realmInstance
        myLibraryDiv(view)
        initializeFlexBoxView(view, R.id.flexboxLayoutCourse, RealmMyCourse::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutTeams, RealmMyTeam::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutMeetups, RealmMeetup::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutMyLife, RealmMyLife::class.java)

        myCoursesResults = mRealm.query<RealmMyCourse>(RealmMyCourse::class).find()
        myTeamsResults = mRealm.query<RealmMyTeam>(RealmMyTeam::class).find()

        scope.launch {
            myCoursesResults.asFlow().collect { _ ->
                updateMyCoursesUI()
            }

            myTeamsResults.asFlow().collect { _ ->
                updateMyTeamsUI()
            }
        }
    }

    private fun updateMyCoursesUI() {
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutCourse) ?: return
        flexboxLayout.removeAllViews()
        setUpMyList(RealmMyCourse::class.java, flexboxLayout, requireView())
    }

    private fun updateMyTeamsUI() {
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutTeams) ?: return
        flexboxLayout.removeAllViews()
        setUpMyList(RealmMyTeam::class.java, flexboxLayout, requireView())
    }

    override fun showResourceDownloadDialog() {
        showDownloadDialog(getLibraryList(mRealm))
    }

    override fun showUserResourceDialog() {
        var dialog: AlertDialog? = null
        val userModelList = mRealm.query<RealmUserModel>(RealmUserModel::class)
            .sort("joinDate", Sort.DESCENDING)
            .find()
        val adapter = UserListArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, userModelList)
        val alertHealthListBinding = AlertHealthListBinding.inflate(LayoutInflater.from(activity))
        alertHealthListBinding.etSearch.visibility = View.GONE
        alertHealthListBinding.spnSort.visibility = View.GONE

        alertHealthListBinding.btnAddMember.setOnClickListener {
            startActivity(Intent(requireContext(), BecomeMemberActivity::class.java))
        }

        alertHealthListBinding.list.adapter = adapter
        alertHealthListBinding.list.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
            val selected = alertHealthListBinding.list.adapter.getItem(i) as RealmUserModel
            showDownloadDialog(getLibraryList(mRealm, selected._id))
            dialog?.dismiss()
        }

        dialog = AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.select_member))
            .setView(alertHealthListBinding.root)
            .setCancelable(false)
            .setNegativeButton(R.string.dismiss, null)
            .create()

        dialog.show()
    }

    override fun syncKeyId() {
        di = DialogUtils.CustomProgressDialog(requireContext())
        di?.setText(getString(R.string.syncing_health_please_wait))
        if (model?.getRoleAsString()?.contains("health") == true) {
            settings?.let { TransactionSyncManager.syncAllHealthData(mRealm, it, this) }
        } else {
            settings?.let { TransactionSyncManager.syncKeyIv(mRealm, it, this) }
        }
    }

    override fun onSyncStarted() {
        di?.show()
    }

    override fun onSyncComplete() {
        di?.dismiss()
        Utilities.toast(activity, getString(R.string.myhealth_synced_successfully))
    }

    override fun onSyncFailed(msg: String?) {
        di?.dismiss()
        Utilities.toast(activity, getString(R.string.myhealth_synced_failed))
    }

    override fun showTaskListDialog() {
        val tasks = mRealm.query<RealmTeamTask>(RealmTeamTask::class,
            "assignee == $0 AND completed == false AND deadline > $1",
            model?.id, Calendar.getInstance().timeInMillis).find()

        if (tasks.isEmpty()) {
            Utilities.toast(requireContext(), getString(R.string.no_due_tasks))
            return
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_expandable_list_item_1, tasks)
        AlertDialog.Builder(requireContext()).setTitle(getString(R.string.due_tasks))
            .setAdapter(adapter) { _, _ -> }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }
}
