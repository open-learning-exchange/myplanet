package org.ole.planet.myplanet.ui.dashboard

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuItemCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.holder.DimenHolder
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.Nameable
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Case
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmObject
import io.realm.RealmResults
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ActivityDashboardBinding
import org.ole.planet.myplanet.databinding.CustomTabBinding
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.SettingActivity
import org.ole.planet.myplanet.ui.chat.ChatHistoryListFragment
import org.ole.planet.myplanet.ui.community.CommunityTabFragment
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationListener
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationsFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment
import org.ole.planet.myplanet.ui.resources.ResourceDetailFragment
import org.ole.planet.myplanet.ui.resources.ResourcesFragment
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission
import org.ole.planet.myplanet.ui.survey.SendSurveyFragment
import org.ole.planet.myplanet.ui.survey.SurveyFragment
import org.ole.planet.myplanet.ui.sync.DashboardElementActivity
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.ui.team.TeamFragment
import org.ole.planet.myplanet.ui.team.TeamPage
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.DialogUtils.guestDialog
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtil
import org.ole.planet.myplanet.utilities.FileUtils.totalAvailableMemoryRatio
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.NotificationUtil
import org.ole.planet.myplanet.utilities.TimeUtils.formatDate
import org.ole.planet.myplanet.utilities.Utilities.toast

@AndroidEntryPoint  
class DashboardActivity : DashboardElementActivity(), OnHomeItemClickListener, NavigationBarView.OnItemSelectedListener, NotificationListener {

    private lateinit var activityDashboardBinding: ActivityDashboardBinding
    private var headerResult: AccountHeader? = null
    var user: RealmUserModel? = null
    var result: Drawer? = null
    private var menul: TabLayout.Tab? = null
    private var menuh: TabLayout.Tab? = null
    private var menuc: TabLayout.Tab? = null
    private var menue: TabLayout.Tab? = null
    private var menuco: TabLayout.Tab? = null
    private var menut: TabLayout.Tab? = null
    private var tl: TabLayout? = null
    private var dl: DrawerLayout? = null
    private val realmListeners = mutableListOf<RealmListener>()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private lateinit var challengeHelper: ChallengeHelper
    private lateinit var notificationManager: NotificationUtil.NotificationManager
    private var notificationsShownThisSession = false

    private interface RealmListener {
        fun removeListener()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkUser()
        initViews()
        updateAppTitle()
        notificationManager = NotificationUtil.getInstance(this)
        if (handleGuestAccess()) return
        setupNavigation()
        handleInitialFragment()
        setupToolbarActions()
        initTabs()
        hideWifi()
        setupRealmListeners()
        checkIfShouldShowNotifications()
        addBackPressCallback()
        challengeHelper = ChallengeHelper(this, mRealm, user, settings, editor, dashboardViewModel)
        challengeHelper.evaluateChallengeDialog()
        handleNotificationIntent(intent)
    }

    private fun initViews() {
        activityDashboardBinding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(activityDashboardBinding.root)
        EdgeToEdgeUtil.setupEdgeToEdge(this, activityDashboardBinding.root)
        setupUI(activityDashboardBinding.activityDashboardParentLayout, this@DashboardActivity)
        setSupportActionBar(activityDashboardBinding.myToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setTitle(R.string.app_project_name)
        activityDashboardBinding.myToolbar.setTitleTextColor(Color.WHITE)
        activityDashboardBinding.myToolbar.setSubtitleTextColor(Color.WHITE)
        navigationView = activityDashboardBinding.topBarNavigation
        navigationView.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
        activityDashboardBinding.appBarBell.bellToolbar.inflateMenu(R.menu.menu_bell_dashboard)
        service = Service(this)
        tl = findViewById(R.id.tab_layout)
        activityDashboardBinding.root.viewTreeObserver.addOnGlobalLayoutListener { topBarVisible() }
        activityDashboardBinding.appBarBell.ivSetting.setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
        }
    }

    private fun updateAppTitle() {
        try {
            val userProfileModel = profileDbHandler.userModel
            if (userProfileModel != null) {
                var name: String? = userProfileModel.getFullName()
                if (name.isNullOrBlank()) {
                    name = profileDbHandler.userModel?.name
                }
                val communityName = settings.getString("communityName", "")
                activityDashboardBinding.appBarBell.appTitleName.text = if (user?.planetCode == "") {
                    "${getString(R.string.planet)} $communityName"
                } else {
                    "${getString(R.string.planet)} ${user?.planetCode}"
                }
            } else {
                activityDashboardBinding.appBarBell.appTitleName.text = getString(R.string.app_project_name)
            }
        } catch (err: Exception) {
            throw RuntimeException(err)
        }
    }

    private fun handleGuestAccess(): Boolean {
        if (user != null && user?.rolesList?.isEmpty() == true && !user?.userAdmin!!) {
            navigationView.visibility = View.GONE
            openCallFragment(InactiveDashboardFragment(), "Dashboard")
            return true
        }
        navigationView.setOnItemSelectedListener(this)
        navigationView.visibility = if (UserProfileDbHandler(this).userModel?.isShowTopbar == true) {
            View.VISIBLE
        } else {
            View.GONE
        }
        return false
    }

    private fun setupNavigation() {
        headerResult = accountHeader
        createDrawer()
        supportFragmentManager.addOnBackStackChangedListener {
            val frag = supportFragmentManager.findFragmentById(R.id.fragment_container)
            val idToSelect = when (frag) {
                is BellDashboardFragment -> 0L
                is ResourcesFragment -> {
                    val isMy = frag.arguments?.getBoolean("isMyCourseLib", false) == true
                    if (isMy) 1L else 3L
                }
                is CoursesFragment -> {
                    val isMy = frag.arguments?.getBoolean("isMyCourseLib", false) == true
                    if (isMy) 2L else 4L
                }
                is TeamFragment -> {
                    val isDashboard = frag.arguments?.getBoolean("fromDashboard", false) == true
                    val isEnterprise = frag.arguments?.getString("type") == "enterprise"
                    if (isDashboard) 0L else if (isEnterprise) 6L else 5L
                }
                is CommunityTabFragment -> 7L
                is SurveyFragment -> 8L
                else -> null
            }
            idToSelect?.let { result?.setSelection(it, false) }
        }
        if (!(user?.id?.startsWith("guest") == true && profileDbHandler.offlineVisits >= 3) &&
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        ) {
            result?.openDrawer()
        }
        result?.actionBarDrawerToggle?.isDrawerIndicatorEnabled = true
        dl = result?.drawerLayout
        topbarSetting()
    }

    private fun handleInitialFragment() {
        if (intent != null && intent.hasExtra("fragmentToOpen")) {
            val fragmentToOpen = intent.getStringExtra("fragmentToOpen")
            if ("feedbackList" == fragmentToOpen) {
                openMyFragment(FeedbackListFragment())
            }
        } else {
            openCallFragment(BellDashboardFragment())
            activityDashboardBinding.appBarBell.bellToolbar.visibility = View.VISIBLE
        }
    }

    private fun setupToolbarActions() {
        activityDashboardBinding.appBarBell.ivSync.setOnClickListener { logSyncInSharedPrefs() }
        activityDashboardBinding.appBarBell.imgLogo.setOnClickListener { result?.openDrawer() }
        activityDashboardBinding.appBarBell.bellToolbar.setOnMenuItemClickListener { item ->
            handleToolbarMenuItem(item.itemId)
            true
        }
    }

    private fun handleToolbarMenuItem(itemId: Int) {
        when (itemId) {
            R.id.action_chat -> {
                if (user?.id?.startsWith("guest") == false) {
                    openCallFragment(
                        ChatHistoryListFragment(),
                        ChatHistoryListFragment::class.java.simpleName
                    )
                } else {
                    guestDialog(this)
                }
            }
            R.id.menu_goOnline -> wifiStatusSwitch()
            R.id.action_sync -> logSyncInSharedPrefs()
            R.id.action_feedback -> {
                if (user?.id?.startsWith("guest") == false) {
                    openCallFragment(
                        FeedbackListFragment(),
                        FeedbackListFragment::class.java.simpleName
                    )
                } else {
                    guestDialog(this)
                }
            }
            R.id.action_settings -> startActivity(Intent(this@DashboardActivity, SettingActivity::class.java))
            R.id.action_disclaimer -> openCallFragment(DisclaimerFragment(), DisclaimerFragment::class.java.simpleName)
            R.id.action_about -> openCallFragment(AboutFragment(), AboutFragment::class.java.simpleName)
            R.id.action_logout -> logout()
            R.id.change_language -> SettingActivity.SettingFragment.languageChanger(this)
            else -> {}
        }
    }

    private fun initTabs() {
        menuh = tl?.getTabAt(0)
        menul = tl?.getTabAt(1)
        menuc = tl?.getTabAt(2)
        menut = tl?.getTabAt(3)
        menue = tl?.getTabAt(4)
        menuco = tl?.getTabAt(5)
    }

    private fun addBackPressCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (result != null && result?.isDrawerOpen == true) {
                    result?.closeDrawer()
                } else {
                    if (supportFragmentManager.backStackEntryCount > 1) {
                        supportFragmentManager.popBackStack()
                    } else {
                        if (!doubleBackToExitPressedOnce) {
                            doubleBackToExitPressedOnce = true
                            toast(MainApplication.context, getString(R.string.press_back_again_to_exit))
                            Handler(Looper.getMainLooper()).postDelayed({ doubleBackToExitPressedOnce = false }, 2000)
                        } else {
                            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                            if (!BuildConfig.LITE && fragment is BaseContainerFragment) {
                                fragment.handleBackPressed()
                            }
                            finish()
                        }
                    }
                }
            }
        })
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val fromNotification = intent?.getBooleanExtra("from_notification", false) ?: false
        if (fromNotification) {
            val notificationType = intent.getStringExtra("notification_type")
            val notificationId = intent.getStringExtra("notification_id")

            notificationId?.let {
                notificationManager.clearNotification("${notificationType}_$it")
            }

            when (notificationType) {
                NotificationUtil.TYPE_SURVEY -> {
                    val surveyId = intent.getStringExtra("surveyId")
                    openCallFragment(SurveyFragment().apply {
                        arguments = Bundle().apply {
                            surveyId?.let { putString("surveyId", it) }
                        }
                    })
                }
                NotificationUtil.TYPE_TASK -> {
                    val taskId = intent.getStringExtra("taskId")
                    openMyFragment(TeamFragment().apply {
                        arguments = Bundle().apply {
                            taskId?.let { putString("taskId", it) }
                        }
                    })
                }
                NotificationUtil.TYPE_STORAGE -> {
                    startActivity(Intent(this, SettingActivity::class.java))
                }
                NotificationUtil.TYPE_JOIN_REQUEST -> {
                    val teamName = intent.getStringExtra("teamName")
                    openMyFragment(TeamFragment().apply {
                        arguments = Bundle().apply {
                            teamName?.let { putString("teamName", it) }
                        }
                    })
                }
                else -> {
                    openNotificationsList(user?.id ?: "")
                }
            }
        }

        if (intent?.getBooleanExtra("auto_navigate", false) == true) {
            isFromNotificationAction = true
            result?.closeDrawer()
            
            val notificationType = intent.getStringExtra("notification_type")
            val relatedId = intent.getStringExtra("related_id")
            
            when (notificationType) {
                NotificationUtil.TYPE_SURVEY -> {
                    handleSurveyNavigation(relatedId)
                }
                NotificationUtil.TYPE_TASK -> {
                    handleTaskNavigation(relatedId)
                }
                NotificationUtil.TYPE_JOIN_REQUEST -> {
                    handleJoinRequestNavigation(relatedId)
                }
                NotificationUtil.TYPE_RESOURCE -> {
                    openCallFragment(ResourcesFragment(), "Resources")
                }
            }

            Handler(Looper.getMainLooper()).postDelayed({
                isFromNotificationAction = false
            }, 1000)
        }
    }
    
    private fun handleSurveyNavigation(surveyId: String?) {
        if (surveyId != null) {
            val currentStepExam = mRealm.where(RealmStepExam::class.java).equalTo("name", surveyId)
                .findFirst()
            AdapterMySubmission.openSurvey(this, currentStepExam?.id, false, false, "")
        }
    }
    
    private fun handleTaskNavigation(taskId: String?) {
        if (taskId != null) {
            val task = mRealm.where(RealmTeamTask::class.java)
                .equalTo("id", taskId)
                .findFirst()

            val linkJson = JSONObject(task?.link ?: "{}")
            val teamId = linkJson.optString("teams")
            if (teamId.isNotEmpty()) {
                val teamObject = mRealm.where(RealmMyTeam::class.java)?.equalTo("_id", teamId)?.findFirst()

                val f = TeamDetailFragment.newInstance(
                    teamId = teamId,
                    teamName = teamObject?.name ?: "",
                    teamType = teamObject?.type ?: "",
                    isMyTeam = true,
                    navigateToPage = TeamPage.TASKS
                )

                openCallFragment(f)
            }
        }
    }
    
    private fun handleJoinRequestNavigation(requestId: String?) {
        if (requestId != null) {
            val actualJoinRequestId = if (requestId.startsWith("join_request_")) {
                requestId.removePrefix("join_request_")
            } else {
                requestId
            }
            val joinRequest = mRealm.where(RealmMyTeam::class.java)
                .equalTo("_id", actualJoinRequestId)
                .equalTo("docType", "request")
                .findFirst()

            val teamId = joinRequest?.teamId

            if (teamId?.isNotEmpty() == true) {
                val f = TeamDetailFragment()
                val b = Bundle()
                b.putString("id", teamId)
                b.putBoolean("isMyTeam", true)
                b.putInt("navigateToPage", TeamPage.JOIN_REQUESTS.ordinal)
                f.arguments = b
                openCallFragment(f)
            }
        }
    }
    private fun setupRealmListeners() {
        if (mRealm.isInTransaction) {
            mRealm.commitTransaction()
        }
        setupListener {
            mRealm.where(RealmMyLibrary::class.java).findAllAsync()
        }

        setupListener {
            mRealm.where(RealmSubmission::class.java)
                .equalTo("userId", user?.id)
                .equalTo("type", "survey")
                .equalTo("status", "pending", Case.INSENSITIVE)
                .findAllAsync()
        }

        setupListener {
            mRealm.where(RealmTeamTask::class.java)
                .notEqualTo("status", "archived")
                .equalTo("completed", false)
                .equalTo("assignee", user?.id)
                .findAllAsync()
        }
    }

    private inline fun <reified T : RealmObject> setupListener(crossinline query: () -> RealmResults<T>) {
        val results = query()
        val listener = RealmChangeListener<RealmResults<T>> { _ ->
            if (notificationsShownThisSession) {
                checkAndCreateNewNotifications()
            }
        }
        results.addChangeListener(listener)
        realmListeners.add(object : RealmListener {
            override fun removeListener() {
                results.removeChangeListener(listener)
            }
        })
    }

    private fun checkIfShouldShowNotifications() {
        val fromLogin = intent.getBooleanExtra("from_login", false)
        if (fromLogin || !notificationsShownThisSession) {
            notificationsShownThisSession = true
            checkAndCreateNewNotifications()
        }
    }

    private fun checkAndCreateNewNotifications() {
        val userId = user?.id

        lifecycleScope.launch(Dispatchers.IO) {
            var unreadCount = 0
            val newNotifications = mutableListOf<NotificationUtil.NotificationConfig>()

            try {
                databaseService.realmInstance.use { backgroundRealm ->
                    backgroundRealm.executeTransaction { realm ->
                        val createdNotifications = createNotifications(realm, userId)
                        newNotifications.addAll(createdNotifications)
                    }

                    unreadCount = dashboardViewModel.getUnreadNotificationsSize(backgroundRealm, userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                try {
                    updateNotificationBadge(unreadCount) {
                        openNotificationsList(userId ?: "")
                    }

                    newNotifications.forEach { config ->
                        notificationManager.showNotification(config)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val newNotifications = mutableListOf<NotificationUtil.NotificationConfig>()

        dashboardViewModel.updateResourceNotification(realm, userId)

        newNotifications.addAll(createSurveyNotifications(realm, userId))
        newNotifications.addAll(createTaskNotifications(realm, userId))
        newNotifications.addAll(createStorageNotification(realm, userId))
        newNotifications.addAll(createJoinRequestNotifications(realm, userId))

        return newNotifications
    }

    private fun createSurveyNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val newNotifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val pendingSurveys = dashboardViewModel.getPendingSurveys(realm, userId)
        val surveyTitles = dashboardViewModel.getSurveyTitlesFromSubmissions(realm, pendingSurveys)

        surveyTitles.forEach { title ->
            val notificationKey = "survey-$title"

            if (!notificationManager.hasNotificationBeenShown(notificationKey)) {
                dashboardViewModel.createNotificationIfNotExists(realm, "survey", title, title, userId)

                val config = notificationManager.createSurveyNotification(title, title)
                newNotifications.add(config)
            }
        }
        return newNotifications
    }

    private fun createTaskNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val newNotifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val tasks = realm.where(RealmTeamTask::class.java)
            .notEqualTo("status", "archived")
            .equalTo("completed", false)
            .equalTo("assignee", userId)
            .findAll()

        tasks.forEach { task ->
            val notificationKey = "task-${task.id}"

            if (!notificationManager.hasNotificationBeenShown(notificationKey)) {
                dashboardViewModel.createNotificationIfNotExists(
                    realm,
                    "task",
                    "${task.title} ${formatDate(task.deadline)}",
                    task.id,
                    userId
                )

                val config = notificationManager.createTaskNotification(
                    task.id ?: "task",
                    task.title ?: "New Task",
                    formatDate(task.deadline)
                )
                newNotifications.add(config)
            }
        }
        return newNotifications
    }

    private fun createStorageNotification(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val newNotifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val storageRatio = totalAvailableMemoryRatio
        val notificationKey = "storage-critical"

        if (storageRatio > 85 && !notificationManager.hasNotificationBeenShown(notificationKey)) {
            dashboardViewModel.createNotificationIfNotExists(realm, "storage", "$storageRatio%", "storage", userId)

            val config = notificationManager.createStorageWarningNotification(storageRatio.toInt())
            newNotifications.add(config)
        }
        return newNotifications
    }

    private fun createJoinRequestNotifications(realm: Realm, userId: String?): List<NotificationUtil.NotificationConfig> {
        val newNotifications = mutableListOf<NotificationUtil.NotificationConfig>()
        val teamLeaderMemberships = realm.where(RealmMyTeam::class.java)
            .equalTo("userId", userId)
            .equalTo("docType", "membership")
            .equalTo("isLeader", true)
            .findAll()

        teamLeaderMemberships.forEach { leadership ->
            val pendingJoinRequests = realm.where(RealmMyTeam::class.java)
                .equalTo("teamId", leadership.teamId)
                .equalTo("docType", "request")
                .findAll()

            pendingJoinRequests.forEach { joinRequest ->
                val notificationKey = "join_request-${joinRequest._id}"

                if (!notificationManager.hasNotificationBeenShown(notificationKey)) {
                    val team = realm.where(RealmMyTeam::class.java)
                        .equalTo("_id", leadership.teamId)
                        .findFirst()

                    val requester = realm.where(RealmUserModel::class.java)
                        .equalTo("id", joinRequest.userId)
                        .findFirst()

                    val requesterName = requester?.name ?: "Unknown User"
                    val teamName = team?.name ?: "Unknown Team"
                    val message = "$requesterName has requested to join $teamName"

                    dashboardViewModel.createNotificationIfNotExists(
                        realm,
                        "join_request",
                        message,
                        joinRequest._id,
                        userId
                    )

                    val config = notificationManager.createJoinRequestNotification(
                        joinRequest._id!!,
                        requesterName,
                        teamName
                    )
                    newNotifications.add(config)
                }
            }
        }
        return newNotifications
    }
    private fun openNotificationsList(userId: String) {
        val fragment = NotificationsFragment().apply {
            arguments = Bundle().apply {
                putString("userId", userId)
            }
            setNotificationUpdateListener(this@DashboardActivity)
        }
        openCallFragment(fragment)
    }

    override fun onNotificationCountUpdated(unreadCount: Int) {
        updateNotificationBadge(unreadCount) {
            openNotificationsList(user?.id ?: "")
        }
    }

    private fun updateNotificationBadge(count: Int, onClickListener: View.OnClickListener) {
        val menuItem = activityDashboardBinding.appBarBell.bellToolbar.menu.findItem(R.id.action_notifications)
        val actionView = MenuItemCompat.getActionView(menuItem)
        val smsCountTxt = actionView.findViewById<TextView>(R.id.notification_badge)
        smsCountTxt.text = "$count"
        smsCountTxt.visibility = if (count > 0) View.VISIBLE else View.GONE
        actionView.setOnClickListener(onClickListener)
    }

    fun refreshChatHistoryList() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is ChatHistoryListFragment) {
            fragment.refreshChatHistoryList()
        }
    }

    private fun hideWifi() {
        val navMenu = activityDashboardBinding.appBarBell.bellToolbar.menu
        navMenu.findItem(R.id.menu_goOnline)
            .setVisible((showBetaFeature(Constants.KEY_SYNC, this)))
    }

    private fun checkUser() {
        user = UserProfileDbHandler(this).userModel
        if (user == null) {
            toast(this, getString(R.string.session_expired))
            logout()
            return
        }
        if (user?.id?.startsWith("guest") == true && profileDbHandler.offlineVisits >= 3) {
            val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            builder.setTitle(getString(R.string.become_a_member))
            builder.setMessage(getString(R.string.trial_period_ended))
            builder.setCancelable(false)
            builder.setPositiveButton(getString(R.string.become_a_member), null)
            builder.setNegativeButton(getString(R.string.menu_logout), null)
            val dialog = builder.create()
            dialog.show()
            val becomeMember = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val logout = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            becomeMember.contentDescription = getString(R.string.confirm_membership)
            logout.contentDescription = getString(R.string.menu_logout)
            becomeMember.setOnClickListener {
                val guest = true
                val intent = Intent(this, BecomeMemberActivity::class.java)
                intent.putExtra("username", profileDbHandler.userModel?.name)
                intent.putExtra("guest", guest)
                setResult(RESULT_OK, intent)
                startActivity(intent)
            }
            logout.setOnClickListener {
                dialog.dismiss()
                logout()
            }
        }
    }

    private fun topBarVisible(){
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        tabLayout.visibility = if (isLandscape) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun topbarSetting() {
        UITheme()
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                onClickTabItems(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                onClickTabItems(tab.position)
            }
        })
        for (i in 0 until tabLayout.tabCount) {
            val customTabBinding = CustomTabBinding.inflate(LayoutInflater.from(this))
            val title = customTabBinding.title
            val icon = customTabBinding.icon
            title.text = tabLayout.getTabAt(i)?.text
            icon.setImageResource(R.drawable.ic_home)
            icon.setImageDrawable(tabLayout.getTabAt(i)?.icon)
            tabLayout.getTabAt(i)?.setCustomView(customTabBinding.root)
        }
        tabLayout.isTabIndicatorFullWidth = false
    }

    private fun UITheme() {
        activityDashboardBinding.appBarBell.bellToolbar.visibility = View.VISIBLE
        activityDashboardBinding.myToolbar.visibility = View.GONE
        navigationView.visibility = View.GONE
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (user?.rolesList?.isEmpty() == true) {
            menu.findItem(R.id.action_setting).isEnabled = false
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private val accountHeader: AccountHeader
        get() {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            var paddingVerticalPx = screenHeight * 0.15
            var paddingHorizontalPx = screenWidth * 0.15
            if(screenWidth > screenHeight){ //sizing for tablets
                paddingVerticalPx = screenHeight * 0.05
                paddingHorizontalPx = screenWidth * 0.05
            }

            val paddingVerticalDp = (paddingVerticalPx / density).toInt()
            val paddingHorizontalDp = (paddingHorizontalPx / density).toInt()
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                ceil(25 * density).toInt()
            }

            val header = AccountHeaderBuilder()
                .withActivity(this@DashboardActivity)
                .withTextColor(ContextCompat.getColor(this, R.color.bg_white))
                .withHeaderBackground(R.drawable.ole_logo)
                .withDividerBelowHeader(false)
                .withTranslucentStatusBar(false)
                .withHeightDp(paddingVerticalDp + 20 * 2 + (statusBarHeight / density).toInt())
                .build()
            val headerBackground = header.headerBackgroundView
            headerBackground.setPadding(
                paddingHorizontalDp, paddingVerticalDp + statusBarHeight + 25,
                paddingHorizontalDp, paddingVerticalDp + 50
            )

            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO ||
                (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && currentNightMode == Configuration.UI_MODE_NIGHT_NO)) {
                headerBackground.setColorFilter(
                    ContextCompat.getColor(this, R.color.md_white_1000),
                    PorterDuff.Mode.SRC_IN
                )
            }
            return header
        }

    private fun createDrawer() {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            ceil(25 * resources.displayMetrics.density).toInt()
        }

        val headerHeight = 160 + (statusBarHeight / resources.displayMetrics.density).toInt()
        val dimenHolder = DimenHolder.fromDp(headerHeight)

        result = headerResult?.let {
            DrawerBuilder().withActivity(this).withFullscreen(true).withTranslucentStatusBar(true).withTranslucentNavigationBar(true)
                .withSliderBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
                .withToolbar(activityDashboardBinding.myToolbar)
                .withAccountHeader(it).withHeaderHeight(dimenHolder)
                .addDrawerItems(*drawerItems).addStickyDrawerItems(*drawerItemsFooter)
                .withOnDrawerItemClickListener { _: View?, _: Int, drawerItem: IDrawerItem<*, *>? ->
                    if (drawerItem != null) {
                        result?.setSelection(drawerItem.identifier, false)
                        menuAction((drawerItem as Nameable<*>).name.textRes)
                    }
                    false
                }.withDrawerWidthDp(200).build()
        }
    }

    private fun menuAction(selectedMenuId: Int) {
        handleDrawerSelection(selectedMenuId)
    }

    private fun handleDrawerSelection(selectedMenuId: Int) {
        when (selectedMenuId) {
            R.string.menu_myplanet -> openCallFragment(BellDashboardFragment())
            R.string.menu_library -> openCallFragment(ResourcesFragment())
            R.string.menu_meetups -> {}
            R.string.menu_surveys -> openCallFragment(SurveyFragment())
            R.string.menu_courses -> openCallFragment(CoursesFragment())
            R.string.menu_community -> openCallFragment(CommunityTabFragment())
            R.string.txt_myLibrary -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(ResourcesFragment())
                }
            }
            R.string.team -> openMyFragment(TeamFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("fromDashboard", false)
                }
            })
            R.string.txt_myCourses -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(CoursesFragment())
                }
            }
            R.string.enterprises -> openEnterpriseFragment()
            R.string.menu_logout -> logout()
            else -> openCallFragment(BellDashboardFragment())
        }
    }

    override fun openMyFragment(f: Fragment) {
        val fragmentName = f::class.java.simpleName
        var tag = "My$fragmentName"
        val isDashboard = f.arguments?.getBoolean("fromDashboard", false) == true
        if(tag != "MyTeamFragment") {
            val b = Bundle()
            b.putBoolean("isMyCourseLib", true)
            f.arguments = b
        }
        if (isDashboard) {
            tag = "MyTeamDashboardFragment"
        }
        when (tag) {
            "MyCoursesFragment" -> result?.setSelection(2, false)
            "MyResourcesFragment" -> result?.setSelection(1, false)
            "MyTeamDashboardFragment" -> result?.setSelection(0, false)
            "MyTeamFragment" ->  result?.setSelection(5, false)
            else -> {
                result?.setSelection(0, false)
            }
        }
        openCallFragment(f, tag)
    }

    override fun onDestroy() {
        super.onDestroy()
        profileDbHandler.onDestroy()
        realmListeners.forEach { it.removeListener() }
        realmListeners.clear()
    }

    override fun openCallFragment(f: Fragment) {
        val tag = f::class.java.simpleName
        openCallFragment(f,tag)
    }

    override fun openLibraryDetailFragment(library: RealmMyLibrary?) {
        val f: Fragment = ResourceDetailFragment()
        val b = Bundle()
        b.putString("libraryId", library?.resourceId)
        f.arguments = b
        openCallFragment(f)
    }

    override fun sendSurvey(current: RealmStepExam?) {
        val f = SendSurveyFragment()
        val b = Bundle()
        b.putString("surveyId", current?.id)
        f.arguments = b
        f.show(supportFragmentManager, "")
    }

    private val drawerItems: Array<IDrawerItem<*, *>>
        get() {
            val menuImageList = ArrayList<Drawable>()
            ResourcesCompat.getDrawable(resources, R.drawable.myplanet, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.ourlibrary, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.ourcourses, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.mylibrary, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.mycourses, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.team, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.business, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.community, null)?.let { menuImageList.add(it) }
            ResourcesCompat.getDrawable(resources, R.drawable.survey, null)?.let { menuImageList.add(it) }
            return arrayOf(
                changeUX(R.string.menu_myplanet, menuImageList[0]).withIdentifier(0),
                changeUX(R.string.txt_myLibrary, menuImageList[1]).withIdentifier(1),
                changeUX(R.string.txt_myCourses, menuImageList[2]).withIdentifier(2),
                changeUX(R.string.menu_library, menuImageList[3]).withIdentifier(3),
                changeUX(R.string.menu_courses, menuImageList[4]).withIdentifier(4),
                changeUX(R.string.team, menuImageList[5]).withIdentifier(5),
                changeUX(R.string.enterprises, menuImageList[6]).withIdentifier(6),
                changeUX(R.string.menu_community, menuImageList[7]).withIdentifier(7),
                changeUX(R.string.menu_surveys, menuImageList[8]).withIdentifier(8)
            )
        }

    private val drawerItemsFooter: Array<IDrawerItem<*, *>>
        get() {
            val menuImageListFooter = ArrayList<Drawable>()
            ResourcesCompat.getDrawable(resources, R.drawable.logout, null)?.let { menuImageListFooter.add(it) }
            return arrayOf(changeUX(R.string.menu_logout, menuImageListFooter[0]))
        }

    private fun changeUX(iconText: Int, drawable: Drawable?): PrimaryDrawerItem {
        return PrimaryDrawerItem().withName(iconText)
            .withIcon(drawable)
            .withTextColor(ContextCompat.getColor(this, R.color.textColorPrimary))
            .withSelectedTextColor(ContextCompat.getColor(this, R.color.primary_dark))
            .withIconColor(ContextCompat.getColor(this, R.color.textColorPrimary))
            .withSelectedIconColor(ContextCompat.getColor(this, R.color.primary_dark))
            .withSelectedColor(ContextCompat.getColor(this, R.color.textColorPrimary))
            .withIconTintingEnabled(true)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        item.isChecked = true
        when (item.itemId) {
            R.id.menu_library -> {
                openCallFragment(ResourcesFragment())
            }
            R.id.menu_courses -> {
                openCallFragment(CoursesFragment())
            }
            R.id.menu_mycourses -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(CoursesFragment())
                }
            }
            R.id.menu_mylibrary -> {
                if (user?.id?.startsWith("guest") == true) {
                    guestDialog(this)
                } else {
                    openMyFragment(ResourcesFragment())
                }
            }
            R.id.menu_enterprises -> {
                openEnterpriseFragment()
            }
            R.id.menu_home -> {
                openCallFragment(BellDashboardFragment())
            }
        }
        item.isChecked = true
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bell_dashboard, menu)
        menu.findItem(R.id.menu_goOnline).isVisible = showBetaFeature(Constants.KEY_SYNC, this)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermissionStatus()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    override fun onNotificationPermissionGranted() {
        super.onNotificationPermissionGranted()
        if (notificationsShownThisSession) {
            checkAndCreateNewNotifications()
        }
    }

    override fun onNotificationPermissionChanged(isEnabled: Boolean) {
        super.onNotificationPermissionChanged(isEnabled)
        if (!isEnabled) {
            showNotificationDisabledReminder()
        }
    }

    private fun showNotificationDisabledReminder() {
        val snackbar = Snackbar.make(
            activityDashboardBinding.root,
            "Notifications are disabled. You might miss important updates.",
            Snackbar.LENGTH_LONG
        )
        snackbar.setAction("Enable") {
            ensureNotificationPermission(true)
        }
        snackbar.show()
    }

    companion object {
        const val MESSAGE_PROGRESS = "message_progress"
        @JvmStatic
        var isFromNotificationAction = false
    }
}
