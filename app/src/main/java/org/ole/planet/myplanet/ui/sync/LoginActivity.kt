package org.ole.planet.myplanet.ui.sync

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityLoginBinding
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.userprofile.TeamListAdapter
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.Utilities

class LoginActivity : SyncActivity(), TeamListAdapter.OnItemClickListener {
    private lateinit var activityLoginBinding: ActivityLoginBinding
    private var guest = false
    var users: List<RealmUserModel>? = null
    private var mAdapter: TeamListAdapter? = null
    private var backPressedTime: Long = 0
    private val BACK_PRESSED_INTERVAL: Long = 2000

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityLoginBinding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(activityLoginBinding.root)
        lblLastSyncDate = activityLoginBinding.lblLastSyncDate
        inputName = activityLoginBinding.inputName
        inputPassword = activityLoginBinding.inputPassword
        btnSignIn = activityLoginBinding.btnSignin
        syncIcon = activityLoginBinding.syncIcon
        becomeMember = activityLoginBinding.becomeMember
        btnGuestLogin = activityLoginBinding.btnGuestLogin
        imgBtnSetting = activityLoginBinding.imgBtnSetting
        syncIcon = activityLoginBinding.syncIcon
        lblVersion = activityLoginBinding.lblVersion
        btnLang = activityLoginBinding.btnLang
        tvAvailableSpace = activityLoginBinding.tvAvailableSpace
        openCommunity = activityLoginBinding.openCommunity
        btnFeedback = activityLoginBinding.btnFeedback
        customDeviceName = activityLoginBinding.customDeviceName

        service = Service(this)

        tvAvailableSpace.text = FileUtils.availableOverTotalMemoryFormattedString
        changeLogoColor()
        declareElements()
        declareMoreElements()
        showWifiDialog()
        registerReceiver()
        forceSync = intent.getBooleanExtra("forceSync", false)
        processedUrl = Utilities.getUrl()
        if (forceSync) {
            isSync = false
        }
        if (intent.hasExtra("versionInfo")) {
            onUpdateAvailable(
                (intent.getSerializableExtra("versionInfo") as MyPlanet?),
                intent.getBooleanExtra("cancelable", false)
            )
        } else {
            service.checkVersion(this, settings)
        }
        checkUsagesPermission()
        forceSyncTrigger()

        if (Utilities.getUrl().isNotEmpty()) {
            openCommunity.visibility = View.VISIBLE
            openCommunity.setOnClickListener {
                HomeCommunityDialogFragment().show(supportFragmentManager, "")
            }
            HomeCommunityDialogFragment().show(supportFragmentManager, "")
        } else {
            openCommunity.visibility = View.GONE
        }
        btnFeedback.setOnClickListener {
            FeedbackFragment().show(supportFragmentManager, "")
        }

        guest = intent.getBooleanExtra("guest", false)
        val username = intent.getStringExtra("username")

        if (guest) {
            resetGuestAsMember(username)
        }
        getTeamMembers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (System.currentTimeMillis() - backPressedTime < BACK_PRESSED_INTERVAL) {
                    finish()
                } else {
                    Utilities.toast(this@LoginActivity, getString(R.string.press_back_again_to_exit))
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })
    }

    fun getTeamMembers() {
        selectedTeamId = prefData.getSELECTEDTEAMID().toString()
        if (selectedTeamId?.isNotEmpty() == true) {
            users = RealmMyTeam.getUsers(selectedTeamId, mRealm, "")
            val userList = (users as MutableList<RealmUserModel>?)?.map {
                User(it.getFullName(), it.name ?: "", "", it.userImage ?: "", "team")
            } ?: emptyList()

            val existingUsers = prefData.getSAVEDUSERS().toMutableList()
            val filteredExistingUsers = existingUsers.filter { it.source != "team" }
            val updatedUserList = userList.filterNot { user -> filteredExistingUsers.any { it.name == user.name } } + filteredExistingUsers
            prefData.setSAVEDUSERS(updatedUserList)
        }

        mAdapter = if (mAdapter == null) {
            TeamListAdapter(prefData.getSAVEDUSERS().toMutableList(), this, this)
        } else {
            mAdapter?.clearList()
            TeamListAdapter(prefData.getSAVEDUSERS().toMutableList(), this, this)
        }

        activityLoginBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        activityLoginBinding.recyclerView.adapter = mAdapter

        val layoutManager: RecyclerView.LayoutManager = object : LinearLayoutManager(this) {
            override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
                return RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        activityLoginBinding.recyclerView.layoutManager = layoutManager
        activityLoginBinding.recyclerView.isNestedScrollingEnabled = true
        activityLoginBinding.recyclerView.setHasFixedSize(true)
    }

    override fun onItemClick(user: User) {
        if (user.password?.isEmpty() == true && user.source != "guest") {
            Glide.with(this)
                .load(user.image)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(activityLoginBinding.userProfile)

            inputName.setText(user.name)
        } else {
            if (user.source == "guest"){
                val model = RealmUserModel.createGuestUser(user.name, mRealm, settings)?.let { mRealm.copyFromRealm(it) }
                if (model == null) {
                    Utilities.toast(this, getString(R.string.unable_to_login))
                } else {
                    saveUserInfoPref(settings, "", model)
                    onLogin()
                }
            } else {
                submitForm(user.name, user.password)
            }
        }
    }
}
