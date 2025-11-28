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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayout
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Sort
import java.util.Calendar
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.NotificationCallback
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.AlertHealthListBinding
import org.ole.planet.myplanet.databinding.ItemLibraryHomeBinding
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
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

@AndroidEntryPoint
open class BaseDashboardFragment : BaseDashboardFragmentPlugin(), NotificationCallback,
    SyncListener {
    private val viewModel: DashboardViewModel by viewModels()
    private var fullName: String? = null
    private var params = LinearLayout.LayoutParams(250, 100)
    private var di: DialogUtils.CustomProgressDialog? = null

    override fun forceDownloadNewsImages() {
        Utilities.toast(activity, getString(R.string.please_select_starting_date))
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(requireActivity(), { _: DatePicker?, i: Int, i1: Int, i2: Int ->
            now[Calendar.YEAR] = i
            now[Calendar.MONTH] = i1
            now[Calendar.DAY_OF_MONTH] = i2
            val imageList = viewModel.uiState.value.dashboardData?.library?.filter { it.isPrivate == true && it.createdDate > now.timeInMillis && it.mediaType == "image" } ?: emptyList()
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

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                uiState.dashboardData?.let { data ->
                    renderUserData(data.user, data.offlineVisits)
                    renderMyLibrary(data.library)
                    renderMyCourses(data.courses)
                    renderMyTeams(data.teams)
                    renderMyLife(data.myLife)
                }
            }
        }
    }

    private fun renderMyLibrary(dbMylibrary: List<RealmMyLibrary>) {
        val flexboxLayout = view?.findViewById<FlexboxLayout>(R.id.flexboxLayout)
        flexboxLayout?.removeAllViews()
        flexboxLayout?.flexDirection = FlexDirection.ROW
        if (dbMylibrary.isEmpty()) {
            view?.findViewById<TextView>(R.id.count_library)?.visibility = View.GONE
        } else {
            view?.findViewById<TextView>(R.id.count_library)?.text =
                getString(R.string.number_placeholder, dbMylibrary.size)
            view?.findViewById<TextView>(R.id.count_library)?.visibility = View.VISIBLE
        }
        for ((itemCnt, items) in dbMylibrary.withIndex()) {
            val itemLibraryHomeBinding =
                ItemLibraryHomeBinding.inflate(LayoutInflater.from(activity))
            val v = itemLibraryHomeBinding.root
            setTextColor(itemLibraryHomeBinding.title, itemCnt)
            val colorResId =
                if (itemCnt % 2 == 0) R.color.card_bg else R.color.dashboard_item_alternative
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
            flexboxLayout?.addView(v, params)
        }
    }

    private fun renderMyCourses(courses: List<RealmMyCourse>) {
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutCourse) ?: return
        flexboxLayout.removeAllViews()
        val filteredCourses = courses.filter { !it.courseTitle.isNullOrBlank() }
        setCountText(filteredCourses.size, RealmMyCourse::class.java, requireView())
        val myCoursesTextViewArray = arrayOfNulls<TextView>(filteredCourses.size)
        for ((itemCnt, items) in filteredCourses.withIndex()) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, items)
            myCoursesTextViewArray[itemCnt]?.let { setTextColor(it, itemCnt) }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params)
        }
    }

    private fun renderMyTeams(teams: List<TeamWithNotification>) {
        val flexboxLayout: FlexboxLayout = view?.findViewById(R.id.flexboxLayoutTeams) ?: return
        flexboxLayout.removeAllViews()
        for ((count, teamWithNotification) in teams.withIndex()) {
            val v = LayoutInflater.from(activity)
                .inflate(R.layout.item_home_my_team, flexboxLayout, false)
            val name = v.findViewById<TextView>(R.id.tv_name)
            setBackgroundColor(v, count)
            if (teamWithNotification.team.teamType == "sync") {
                name.setTypeface(null, Typeface.BOLD)
            }
            handleClick(
                teamWithNotification.team._id,
                teamWithNotification.team.name,
                TeamDetailFragment(),
                name
            )
            showNotificationIcons(teamWithNotification, v)
            name.text = teamWithNotification.team.name
            flexboxLayout.addView(v, params)
        }
        setCountText(teams.size, RealmMyTeam::class.java, requireView())
    }

    private fun showNotificationIcons(teamWithNotification: TeamWithNotification, v: View) {
        val imgTask = v.findViewById<ImageView>(R.id.img_task)
        val imgChat = v.findViewById<ImageView>(R.id.img_chat)
        imgChat.visibility = if (teamWithNotification.hasUnreadChat) View.VISIBLE else View.GONE
        imgTask.visibility = if (teamWithNotification.hasPendingTask) View.VISIBLE else View.GONE
    }

    private fun renderMyLife(myLife: List<RealmMyLife>) {
        val flexboxLayout = view?.findViewById<FlexboxLayout>(R.id.flexboxLayoutMyLife)
        flexboxLayout?.removeAllViews()
        val visibleMyLife = myLife.filter { it.isVisible }
        for ((itemCnt, items) in visibleMyLife.withIndex()) {
            flexboxLayout?.addView(getLayout(itemCnt, items), params)
        }
    }

    private fun renderUserData(user: RealmUserModel?, offlineVisits: Int) {
        if (view == null) return
        val v = requireView()
        model = user
        fullName = user?.getFullName()
        if (fullName?.trim().isNullOrBlank()) {
            fullName = user?.name
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.VISIBLE
            v.findViewById<LinearLayout>(R.id.ll_prompt).setOnClickListener {
                if (!childFragmentManager.isStateSaved) {
                    UserInformationFragment.getInstance("", "", false)
                        .show(childFragmentManager, "")
                }
            }
        } else {
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.GONE
        }
        v.findViewById<ImageView>(R.id.ic_close).setOnClickListener {
            v.findViewById<LinearLayout>(R.id.ll_prompt).visibility = View.GONE
        }
        val imageView = v.findViewById<ImageView>(R.id.imageView)
        if (!TextUtils.isEmpty(user?.userImage)) {
            Glide.with(requireActivity())
                .load(user?.userImage)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(200, 200)
                .circleCrop()
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.profile)
        }
        v.findViewById<TextView>(R.id.txtRole).text =
            getString(R.string.user_role, user?.getRoleAsString())
        v.findViewById<TextView>(R.id.txtFullName).text =
            getString(R.string.user_name, fullName, offlineVisits)
    }

    private fun myLibraryItemClickAction(textView: TextView, items: RealmMyLibrary?) {
        textView.setOnClickListener {
            items?.let {
                openResource(it)
            }
        }
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
        val userId = settings?.getString("userId", "--") ?: ""
        viewModel.loadDashboardData(userId)
        observeUiState()

        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutCourse).flexDirection = FlexDirection.ROW
        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutTeams).flexDirection = FlexDirection.ROW
        view.findViewById<FlexboxLayout>(R.id.flexboxLayoutMyLife).flexDirection = FlexDirection.ROW
    }

    override fun showResourceDownloadDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val libraries = viewModel.uiState.value.dashboardData?.library ?: emptyList()
            showDownloadDialog(libraries)
        }
    }

    override fun showUserResourceDialog() {
        var dialog: AlertDialog? = null
        val userModelList = viewModel.uiState.value.dashboardData?.user?.let { listOf(it) } ?: emptyList()
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
            val libraries = viewModel.uiState.value.dashboardData?.library?.filter { it.userIds?.contains(selected.id) == true } ?: emptyList()
            showDownloadDialog(libraries)
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
        // todo: refactor this to use the view model
    }

    override fun onSyncStarted() {
        di = DialogUtils.getCustomProgressDialog(activity)
        di?.show()
    }

    override fun onSyncComplete() {
        di?.dismiss()
    }

    override fun onSyncFailed(msg: String?) {
        di?.dismiss()
    }
}
