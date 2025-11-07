package org.ole.planet.myplanet.ui.dashboard

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.DatePicker
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import io.realm.Case
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.Sort
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.TransactionSyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler.Companion.KEY_LOGIN
import org.ole.planet.myplanet.ui.exam.UserInformationFragment
import org.ole.planet.myplanet.ui.myhealth.UserListArrayAdapter
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.ui.userprofile.UserProfileFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DownloadUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities

open class BaseDashboardFragment : BaseDashboardFragmentPlugin(), NotificationCallback,
    SyncListener {
    private var fullName: String? = null
    private var params = LinearLayout.LayoutParams(250, 100)
    private var di: DialogUtils.CustomProgressDialog? = null
    private lateinit var myCoursesResults: RealmResults<RealmMyCourse>
    private val myCoursesChangeListener = RealmChangeListener<RealmResults<RealmMyCourse>> { _ ->
        updateMyCoursesUI()
    }
    private lateinit var myTeamsResults: RealmResults<RealmMyTeam>
    private val myTeamsChangeListener = RealmChangeListener<RealmResults<RealmMyTeam>> { _ ->
        updateMyTeamsUI()
    }
    private lateinit var offlineActivitiesResults: RealmResults<RealmOfflineActivity>
    fun onLoaded(v: View) {
        model = profileDbHandler.userModel
        fullName = profileDbHandler.userModel?.getFullName()
        if (fullName?.trim().isNullOrBlank()) {
            fullName = profileDbHandler.userModel?.name
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.VISIBLE
            v.findViewById<LinearLayout>(R.id.ll_prompt).setOnClickListener {
                if (!childFragmentManager.isStateSaved) {
                    UserInformationFragment.getInstance("", "", false).show(childFragmentManager, "")
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

        if (mRealm.isInTransaction) {
            mRealm.commitTransaction()
        }

        offlineActivitiesResults = mRealm.where(RealmOfflineActivity::class.java)
            .equalTo("userName", profileDbHandler.userModel?.name)
            .equalTo("type", KEY_LOGIN)
            .findAllAsync()
        v.findViewById<TextView>(R.id.txtRole).text = getString(R.string.user_role, model?.getRoleAsString())
        lifecycleScope.launch {
            val offlineVisits = profileDbHandler.getOfflineVisitsAsync(fullName)
            v.findViewById<TextView>(R.id.txtFullName).text = getString(R.string.user_name, fullName, offlineVisits)
        }
    }

    override fun forceDownloadNewsImages() {
        Utilities.toast(activity, getString(R.string.please_select_starting_date))
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(requireActivity(), { _: DatePicker?, i: Int, i1: Int, i2: Int ->
            now[Calendar.YEAR] = i
            now[Calendar.MONTH] = i1
            now[Calendar.DAY_OF_MONTH] = i2
            val imageList = mRealm.where(RealmMyLibrary::class.java).equalTo("isPrivate", true)
                .greaterThan("createdDate", now.timeInMillis).equalTo("mediaType", "image")
                .findAll()
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
        if (!FileUtils.checkFileExist(requireContext(), Constants.DICTIONARY_URL)) {
            Utilities.toast(activity, getString(R.string.downloading_started_please_check_notification))
            DownloadUtils.openDownloadService(activity, list, false)
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
            val colorResId = if (itemCnt % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
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
        val userId = settings?.getString("userId", "--")
        lifecycleScope.launch {
            setUpMyLife(userId)
            when (c) {
                RealmMyCourse::class.java -> {
                    val dbMycourses = loadMyCourses()
                    renderMyCourses(dbMycourses, flexboxLayout, view)
                }

                RealmMyTeam::class.java -> {
                    val teamData = loadMyTeams()
                    renderMyTeams(teamData, flexboxLayout, view)
                }

                RealmMyLife::class.java -> {
                    val dbMylife = loadMyLifeData()
                    renderMyLife(dbMylife, flexboxLayout)
                }

                else -> {
                    val otherData = loadOtherData(c, userId)
                    renderOtherData(otherData, flexboxLayout, view, c)
                }
            }
        }
    }

    private suspend fun loadMyCourses(): List<RealmMyCourse> = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val courses = RealmMyCourse.getMyByUserId(realm, settings).filter { !it.courseTitle.isNullOrBlank() }
            realm.copyFromRealm(courses)
        }
    }

    private fun renderMyCourses(dbMycourses: List<RealmMyCourse>, flexboxLayout: FlexboxLayout, view: View) {
        setCountText(dbMycourses.size, RealmMyCourse::class.java, view)
        val myCoursesTextViewArray = arrayOfNulls<TextView>(dbMycourses.size)
        for ((itemCnt, items) in dbMycourses.withIndex()) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, items)
            myCoursesTextViewArray[itemCnt]?.let { setTextColor(it, itemCnt) }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params)
        }
    }

    private suspend fun loadMyTeams(): List<TeamData> = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val dbMyTeam = RealmMyTeam.getMyTeamsByUserId(realm, settings)
            val userId = profileDbHandler.userModel?.id
            val teamData = dbMyTeam.map { team ->
                val notification = realm.where(RealmTeamNotification::class.java)
                    .equalTo("parentId", team._id).equalTo("type", "chat").findFirst()
                val chatCount = realm.where(RealmNews::class.java).equalTo("viewableBy", "teams")
                    .equalTo("viewableId", team._id).count()
                val tasks = realm.where(RealmTeamTask::class.java).equalTo("assignee", userId)
                    .between(
                        "deadline",
                        Calendar.getInstance().timeInMillis,
                        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
                    )
                    .findAll()
                TeamData(
                    realm.copyFromRealm(team),
                    notification != null && notification.lastCount < chatCount,
                    tasks.isNotEmpty()
                )
            }
            teamData
        }
    }


    private fun renderMyTeams(teamData: List<TeamData>, flexboxLayout: FlexboxLayout, view: View) {
        setCountText(teamData.size, RealmMyTeam::class.java, view)
        for ((count, data) in teamData.withIndex()) {
            val v = LayoutInflater.from(activity).inflate(R.layout.item_home_my_team, flexboxLayout, false)
            val name = v.findViewById<TextView>(R.id.tv_name)
            setBackgroundColor(v, count)
            if (data.team.teamType == "sync") {
                name.setTypeface(null, Typeface.BOLD)
            }
            handleClick(data.team._id, data.team.name, TeamDetailFragment(), name)
            v.findViewById<ImageView>(R.id.img_chat).visibility = if (data.hasUnreadMessages) View.VISIBLE else View.GONE
            v.findViewById<ImageView>(R.id.img_task).visibility = if (data.hasUpcomingTasks) View.VISIBLE else View.GONE
            name.text = data.team.name
            flexboxLayout.addView(v, params)
        }
    }

    private suspend fun loadMyLifeData(): List<RealmMyLife> = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val rawMylife: List<RealmMyLife> = RealmMyLife.getMyLifeByUserId(realm, settings)
            realm.copyFromRealm(rawMylife.filter { it.isVisible })
        }
    }


    private fun renderMyLife(dbMylife: List<RealmMyLife>, flexboxLayout: FlexboxLayout) {
        for ((itemCnt, items) in dbMylife.withIndex()) {
            flexboxLayout.addView(getLayout(itemCnt, items), params)
        }
    }

    private suspend fun loadOtherData(c: Class<out RealmObject>, userId: String?): List<out RealmObject> = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            userId?.let {
                val results = realm.where(c).contains("userId", it, Case.INSENSITIVE).findAll()
                realm.copyFromRealm(results)
            } ?: emptyList()
        }
    }

    private fun renderOtherData(data: List<out RealmObject>, flexboxLayout: FlexboxLayout, view: View, c: Class<out RealmObject>) {
        // The original code had a generic rendering loop here, but it would crash
        // because it tried to cast all objects to RealmMyCourse.
        // Since there are no callers that would hit this else case, leaving this empty
        // is safer and prevents a potential crash.
    }

    data class TeamData(
        val team: RealmMyTeam,
        val hasUnreadMessages: Boolean,
        val hasUpcomingTasks: Boolean
    )


    private suspend fun setUpMyLife(userId: String?) = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val realmObjects = RealmMyLife.getMyLifeByUserId(realm, settings)
            if (realmObjects.isEmpty()) {
                realm.executeTransaction {
                    val myLifeListBase = getMyLifeListBase(userId)
                    var weight = 1
                    for (item in myLifeListBase) {
                        var ml: RealmMyLife
                        ml = it.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                        ml.title = item.title
                        ml.imageId = item.imageId
                        ml.weight = weight
                        ml.userId = item.userId
                        ml.isVisible = true
                        weight++
                    }
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
        if (::myCoursesResults.isInitialized) {
            myCoursesResults.removeChangeListener(myCoursesChangeListener)
        }
        if (::myTeamsResults.isInitialized) {
            myTeamsResults.removeChangeListener(myTeamsChangeListener)
        }
        if (isRealmInitialized()) {
            mRealm.removeAllChangeListeners()
            if (mRealm.isInTransaction) {
                mRealm.cancelTransaction()
            }
            mRealm.close()
        }
        super.onDestroy()
    }

    private fun setCountText(countText: Int, c: Class<*>, v: View) {
        when (c) {
            RealmMyCourse::class.java -> {
                updateCountText(countText, v.findViewById(R.id.count_course))
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
        myLibraryDiv(view)
        initializeFlexBoxView(view, R.id.flexboxLayoutCourse, RealmMyCourse::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutTeams, RealmMyTeam::class.java)
        initializeFlexBoxView(view, R.id.flexboxLayoutMyLife, RealmMyLife::class.java)

        if (mRealm.isInTransaction) {
            mRealm.commitTransaction()
        }
        myCoursesResults = RealmMyCourse.getMyByUserId(mRealm, settings)
        myTeamsResults = RealmMyTeam.getMyTeamsByUserId(mRealm, settings)

        myCoursesResults.addChangeListener(myCoursesChangeListener)
        myTeamsResults.addChangeListener(myTeamsChangeListener)
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
        viewLifecycleOwner.lifecycleScope.launch {
            showDownloadDialog(getLibraryList(mRealm))
        }
    }

    override fun showUserResourceDialog() {
        var dialog: AlertDialog? = null
        val userModelList = mRealm.where(RealmUserModel::class.java).sort("joinDate", Sort.DESCENDING).findAll()
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
    }

    override fun onSyncFailed(msg: String?) {
        di?.dismiss()
    }

}
