package org.ole.planet.myplanet.ui.team

import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.EnhancedSyncListener
import org.ole.planet.myplanet.callback.MemberChangeListener
import org.ole.planet.myplanet.databinding.FragmentTeamDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getJoinedMemberCount
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.syncTeamActivities
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

class TeamDetailFragment : BaseTeamFragment(), MemberChangeListener {
    private lateinit var fragmentTeamDetailBinding: FragmentTeamDetailBinding
    private var directTeamName: String? = null
    private var directTeamType: String? = null
    private var directTeamId: String? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    lateinit var settings: SharedPreferences
    private var isDataLoading = false
    private var dataRefreshHandler: Handler? = null
    private var loadingIndicator: View? = null
    private var loadingText: TextView? = null
    private var dataReadyCounter = 0

    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        startTeamSync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentTeamDetailBinding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        directTeamId = requireArguments().getString("teamId")
        directTeamName = requireArguments().getString("teamName")
        directTeamType = requireArguments().getString("teamType")

        val teamId = requireArguments().getString("id") ?: ""
        val isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        val user = UserProfileDbHandler(requireContext()).userModel
        mRealm = DatabaseService(requireActivity()).realmInstance

        if (shouldQueryRealm(teamId)) {
            if (teamId.isNotEmpty()) {
                team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                    ?: throw IllegalArgumentException("Team not found for ID: $teamId")
            }
        } else {
            val effectiveTeamId = directTeamId ?: teamId
            if (effectiveTeamId.isNotEmpty()) {
                team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", effectiveTeamId).findFirst()
            }
        }

        setupTeamDetails(isMyTeam, user)

        return fragmentTeamDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize loading indicator and refresh handler
        setupLoadingIndicator(view)
        setupDataRefreshHandler()

        createTeamLog()
    }

    private fun setupLoadingIndicator(view: View) {
        // Try to find existing loading views in XML
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        loadingText = view.findViewById(R.id.loading_text)

        // If views don't exist in XML, create them programmatically
        if (loadingIndicator == null) {
            createLoadingViews()
        }
    }

    private fun createLoadingViews() {
        // Create loading indicator programmatically if not in XML
        val rootLayout = fragmentTeamDetailBinding.root as? ViewGroup

        val loadingLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16)
        }

        val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        loadingText = TextView(requireContext()).apply {
            text = "Loading team data..."
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }

        loadingLayout.addView(progressBar)
        loadingLayout.addView(loadingText)

        loadingIndicator = loadingLayout
        rootLayout?.addView(loadingLayout, 1) // Add after header but before tabs
    }

    private fun setupDataRefreshHandler() {
        dataRefreshHandler = Handler(Looper.getMainLooper())
    }

    private fun startTeamSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isTeamsSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        isDataLoading = true
        dataReadyCounter = 0

        SyncManager.instance?.start(object : EnhancedSyncListener {
            override fun onSyncStarted() {
                activity?.runOnUiThread {
                    if (isAdded && !requireActivity().isFinishing) {
                        // Show both progress dialog and loading indicator
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(requireContext().getString(R.string.syncing_team_data))
                        customProgressDialog?.show()

                        showLoadingState()
                        startPeriodicDataRefresh()
                    }
                }
            }

            override fun onProgressUpdate(processName: String, itemsProcessed: Int) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        // Update loading text with progress
                        loadingText?.text = "Loading $processName: $itemsProcessed items"

                        // Update progress dialog
                        customProgressDialog?.setText("$processName: $itemsProcessed items processed")
                    }
                }
            }

            override fun onDataReady(dataType: String) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        dataReadyCounter++
                        loadingText?.text = "$dataType data ready"

                        // Refresh data immediately when ready
                        refreshTeamDataSilently()

                        // Dismiss progress dialog after first data is ready but keep loading indicator
                        if (dataReadyCounter == 1) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                        }
                    }
                }
            }

            override fun onSyncComplete() {
                activity?.runOnUiThread {
                    if (isAdded) {
                        // Dismiss progress dialog if still showing
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        // Final data refresh and hide loading
                        refreshTeamData()
                        hideLoadingState()
                        stopPeriodicDataRefresh()

                        prefManager.setTeamsSynced(true)
                        isDataLoading = false
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        hideLoadingState()
                        stopPeriodicDataRefresh()
                        isDataLoading = false

                        Snackbar.make(fragmentTeamDetailBinding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                            .setAction("Retry") { startTeamSync() }
                            .show()
                    }
                }
            }
        }, "full", listOf("tasks", "meetups", "team_activities"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    // NEW: Progressive loading UI methods
    private fun showLoadingState() {
        loadingIndicator?.visibility = View.VISIBLE
        loadingText?.text = "Preparing team sync..."

        // Optionally hide some UI elements while loading
        fragmentTeamDetailBinding.tabLayout.visibility = View.GONE
        fragmentTeamDetailBinding.viewPager2.visibility = View.GONE
    }

    private fun hideLoadingState() {
        loadingIndicator?.visibility = View.GONE

        // Restore UI elements
        fragmentTeamDetailBinding.tabLayout.visibility = View.VISIBLE
        fragmentTeamDetailBinding.viewPager2.visibility = View.VISIBLE
    }

    private fun startPeriodicDataRefresh() {
        val refreshRunnable = object : Runnable {
            override fun run() {
                if (isDataLoading && isAdded) {
                    refreshTeamDataSilently()
                    dataRefreshHandler?.postDelayed(this, 2000) // Refresh every 2 seconds
                }
            }
        }
        dataRefreshHandler?.postDelayed(refreshRunnable, 2000)
    }

    private fun stopPeriodicDataRefresh() {
        dataRefreshHandler?.removeCallbacksAndMessages(null)
    }

    // Silent refresh that doesn't show/hide loading states
    private fun refreshTeamDataSilently() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            val teamId = requireArguments().getString("id") ?: directTeamId ?: ""
            val isMyTeam = requireArguments().getBoolean("isMyTeam", false)

            if (teamId.isNotEmpty()) {
                val updatedTeam = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
                if (updatedTeam != null) {
                    team = updatedTeam

                    // Only update UI if we have data and we're not in initial loading state
                    if (!isDataLoading || dataReadyCounter > 0) {
                        fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(requireActivity(), team, isMyTeam, this)
                        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
                            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
                        }.attach()

                        fragmentTeamDetailBinding.title.text = getEffectiveTeamName()
                        fragmentTeamDetailBinding.subtitle.text = getEffectiveTeamType()

                        if(getJoinedMemberCount(team!!._id.toString(), mRealm) <= 1 && isMyTeam){
                            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
                        } else {
                            fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE
                        }

                        // Show UI elements if data is available
                        if (dataReadyCounter > 0) {
                            fragmentTeamDetailBinding.tabLayout.visibility = View.VISIBLE
                            fragmentTeamDetailBinding.viewPager2.visibility = View.VISIBLE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Enhanced version of existing refreshTeamData
    private fun refreshTeamData() {
        refreshTeamDataSilently()

        // Ensure UI is fully visible after loading is complete
        if (!isDataLoading) {
            fragmentTeamDetailBinding.tabLayout.visibility = View.VISIBLE
            fragmentTeamDetailBinding.viewPager2.visibility = View.VISIBLE
        }
    }

    private fun setupTeamDetails(isMyTeam: Boolean, user: RealmUserModel?) {
        fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(requireActivity(), team, isMyTeam, this)
        TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
            tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
        }.attach()

        val pageOrdinal = arguments?.getInt("navigateToPage", -1) ?: -1
        if (pageOrdinal >= 0 &&
            pageOrdinal < (fragmentTeamDetailBinding.viewPager2.adapter?.itemCount ?: 0)
        ) {
            fragmentTeamDetailBinding.viewPager2.currentItem = pageOrdinal
        }

        fragmentTeamDetailBinding.title.text = getEffectiveTeamName()
        fragmentTeamDetailBinding.subtitle.text = getEffectiveTeamType()

        if (!isMyTeam) {
            setupNonMyTeamButtons(user)
        } else {
            setupMyTeamButtons(user)
        }

        if(getJoinedMemberCount(team!!._id.toString(), mRealm) <= 1 && isMyTeam){
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        }
    }

    private fun setupNonMyTeamButtons(user: RealmUserModel?) {
        fragmentTeamDetailBinding.btnAddDoc.isEnabled = false
        fragmentTeamDetailBinding.btnAddDoc.visibility = View.GONE
        fragmentTeamDetailBinding.btnLeave.isEnabled = true
        fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE

        if (user?.id?.startsWith("guest") == true){
            fragmentTeamDetailBinding.btnLeave.isEnabled = false
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        }

        val currentTeam = team
        if (currentTeam != null && !currentTeam._id.isNullOrEmpty()) {
            val isUserRequested = currentTeam.requested(user?.id, mRealm)
            if (isUserRequested) {
                fragmentTeamDetailBinding.btnLeave.text = getString(R.string.requested)
                fragmentTeamDetailBinding.btnLeave.isEnabled = false
            } else {
                fragmentTeamDetailBinding.btnLeave.text = getString(R.string.join)
                fragmentTeamDetailBinding.btnLeave.setOnClickListener {
                    RealmMyTeam.requestToJoin(currentTeam._id!!, user, mRealm, team?.teamType)
                    fragmentTeamDetailBinding.btnLeave.text = getString(R.string.requested)
                    fragmentTeamDetailBinding.btnLeave.isEnabled = false
                    syncTeamActivities(requireContext())
                }
            }
        } else {
            throw IllegalStateException("Team or team ID is null, cannot proceed.")
        }
    }

    private fun setupMyTeamButtons(user: RealmUserModel?) {
        fragmentTeamDetailBinding.btnAddDoc.isEnabled = true
        fragmentTeamDetailBinding.btnAddDoc.visibility = View.VISIBLE
        fragmentTeamDetailBinding.btnLeave.isEnabled = true
        fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE

        fragmentTeamDetailBinding.btnLeave.setOnClickListener {
            AlertDialog.Builder(requireContext()).setMessage(R.string.confirm_exit)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    team?.leave(user, mRealm)
                    Utilities.toast(activity, getString(R.string.left_team))
                    fragmentTeamDetailBinding.viewPager2.adapter = TeamPagerAdapter(requireActivity(), team, false, this)
                    TabLayoutMediator(fragmentTeamDetailBinding.tabLayout, fragmentTeamDetailBinding.viewPager2) { tab, position ->
                        tab.text = (fragmentTeamDetailBinding.viewPager2.adapter as TeamPagerAdapter).getPageTitle(position)
                    }.attach()
                    fragmentTeamDetailBinding.llActionButtons.visibility = View.GONE
                }.setNegativeButton(R.string.no, null).show()
        }

        fragmentTeamDetailBinding.btnAddDoc.setOnClickListener {
            MainApplication.showDownload = true
            fragmentTeamDetailBinding.viewPager2.currentItem = 6
            MainApplication.showDownload = false
            if (MainApplication.listener != null) {
                MainApplication.listener?.onAddDocument()
            }
        }
    }

    override fun onMemberChanged() {
        if(getJoinedMemberCount("${team?._id}", mRealm) <= 1){
            fragmentTeamDetailBinding.btnLeave.visibility = View.GONE
        } else{
            fragmentTeamDetailBinding.btnLeave.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when user returns to fragment
        if (!isDataLoading) {
            refreshTeamDataSilently()
        }
    }

    override fun onNewsItemClick(news: RealmNews?) {}

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun createTeamLog() {
        val userModel = UserProfileDbHandler(requireContext()).userModel ?: return
        val userName = userModel.name
        val userPlanetCode = userModel.planetCode
        val userParentCode = userModel.parentCode
        val teamType = getEffectiveTeamType()

        CoroutineScope(Dispatchers.IO).launch {
            val realm = DatabaseService(requireActivity()).realmInstance

            realm.executeTransaction { r ->
                val log = r.createObject(RealmTeamLog::class.java, "${UUID.randomUUID()}")
                log.teamId = getEffectiveTeamId()
                log.user = userName
                log.createdOn = userPlanetCode
                log.type = "teamVisit"
                log.teamType = teamType
                log.parentCode = userParentCode
                log.time = Date().time
            }

            realm.close()
        }
    }

    private fun shouldQueryRealm(teamId: String): Boolean {
        return teamId.isNotEmpty()
    }

    override fun onDestroy() {
        super.onDestroy()
        customProgressDialog?.dismiss()
        customProgressDialog = null
        stopPeriodicDataRefresh()
        dataRefreshHandler = null
    }

    companion object {
        fun newInstance(
            teamId: String,
            teamName: String,
            teamType: String,
            isMyTeam: Boolean,
            navigateToPage: TeamPage? = null
        ): TeamDetailFragment {
            val fragment = TeamDetailFragment()
            val args = Bundle().apply {
                putString("teamId", teamId)
                putString("teamName", teamName)
                putString("teamType", teamType)
                putBoolean("isMyTeam", isMyTeam)
                navigateToPage?.let { putInt("navigateToPage", it.ordinal) }
            }
            fragment.arguments = args
            return fragment
        }
    }
}
