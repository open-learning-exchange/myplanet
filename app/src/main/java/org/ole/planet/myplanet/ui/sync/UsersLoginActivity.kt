package org.ole.planet.myplanet.ui.sync

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityUsersLoginBinding
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.userprofile.TeamListAdapter
import org.ole.planet.myplanet.utilities.Utilities

class UsersLoginActivity : SyncActivity(), TeamListAdapter.OnItemClickListener {
    private lateinit var activityUsersLoginBinding: ActivityUsersLoginBinding
    private var guest = false
    var users: List<RealmUserModel>? = null
    var mAdapter: TeamListAdapter? = null
    private var backPressedTime: Long = 0
    private val BACK_PRESSED_INTERVAL: Long = 2000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityUsersLoginBinding = ActivityUsersLoginBinding.inflate(layoutInflater)
        setContentView(activityUsersLoginBinding.root)
        lblLastSyncDate = activityUsersLoginBinding.lblLastSyncDate
        inputName = activityUsersLoginBinding.inputName
        inputPassword = activityUsersLoginBinding.inputPassword
        btnSignIn = activityUsersLoginBinding.btnSignin
        syncIcon = activityUsersLoginBinding.syncIcon
        becomeMember = activityUsersLoginBinding.becomeMember
        btnGuestLogin = activityUsersLoginBinding.btnGuestLogin
        imgBtnSetting = activityUsersLoginBinding.imgBtnSetting
        syncIcon = activityUsersLoginBinding.syncIcon
        lblVersion = activityUsersLoginBinding.lblVersion
        service = Service(this)

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
                (intent.getSerializableExtra("versionInfo") as MyPlanet?)!!,
                intent.getBooleanExtra("cancelable", false)
            )
        } else {
            service.checkVersion(this, settings)
        }
        checkUsagesPermission()
        setUpChildMode()
        forceSyncTrigger()

        if (Utilities.getUrl().isNotEmpty()) {
            activityUsersLoginBinding.openCommunity.visibility = View.VISIBLE
            activityUsersLoginBinding.openCommunity.setOnClickListener {
                HomeCommunityDialogFragment().show(supportFragmentManager, "")
            }
            HomeCommunityDialogFragment().show(supportFragmentManager, "")
        } else {
            activityUsersLoginBinding.openCommunity.visibility = View.GONE
        }
        activityUsersLoginBinding.btnFeedback.setOnClickListener {
            FeedbackFragment().show(supportFragmentManager, "")
        }

        guest = intent.getBooleanExtra("guest", false)
        val username = intent.getStringExtra("username")

        if (guest) {
            resetGuestAsMember(username)
        }
        getTeamMembers()
    }

    fun getTeamMembers() {
        selectedTeamId = prefData.getSELECTEDTEAMID().toString()
        users = RealmMyTeam.getUsers(selectedTeamId, mRealm, "")

        val userList = (users as MutableList<RealmUserModel>?)?.map {
            User(it.fullName ?: "", it.name ?: "", "", it.userImage ?: "", "team")
        } ?: emptyList()

        val existingUsers = prefData.getSAVEDUSERS().toMutableList()
        val filteredExistingUsers = existingUsers.filter { it.source != "team" }

        val updatedUserList = userList.filterNot { user ->
            filteredExistingUsers.any { it.name == user.name }
        } + filteredExistingUsers
        prefData.setSAVEDUSERS(updatedUserList)

        mAdapter = if (mAdapter == null) {
            TeamListAdapter(prefData.getSAVEDUSERS().toMutableList(), this, this)
        } else {
            mAdapter!!.clearList()
            TeamListAdapter(prefData.getSAVEDUSERS().toMutableList(), this, this)
        }

        activityUsersLoginBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        activityUsersLoginBinding.recyclerView.adapter = mAdapter

        val layoutManager: RecyclerView.LayoutManager = object : LinearLayoutManager(this) {
            override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
                return RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        activityUsersLoginBinding.recyclerView.layoutManager = layoutManager
        activityUsersLoginBinding.recyclerView.isNestedScrollingEnabled = true
        activityUsersLoginBinding.recyclerView.setHasFixedSize(true)
    }

    override fun onItemClick(user: User) {
        if (user.password.isEmpty() && user.source != "guest") {
            Glide.with(this)
                .load(user.image)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(activityUsersLoginBinding.userProfile)

            activityUsersLoginBinding.inputName.setText(user.name)
        } else {
            if (user.source == "guest"){
                val model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(user.name, mRealm, settings))
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

    override fun onBackPressed() {
        if (System.currentTimeMillis() - backPressedTime < BACK_PRESSED_INTERVAL) {
            super.onBackPressed()
        } else {
            Utilities.toast(this, getString(R.string.press_back_again_to_exit))
            backPressedTime = System.currentTimeMillis()
        }
    }
}
