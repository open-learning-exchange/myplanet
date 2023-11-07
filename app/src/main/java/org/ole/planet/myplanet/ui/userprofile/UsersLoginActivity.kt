package org.ole.planet.myplanet.ui.userprofile

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityUsersLoginBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.sync.UserListAdapter

class UsersLoginActivity : AppCompatActivity(), TeamListAdapter.OnItemClickListener{
    private lateinit var activityUsersLoginBinding: ActivityUsersLoginBinding
    private lateinit var mRealm: Realm
    var users: List<RealmUserModel>? = null
    var mAdapter: TeamListAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityUsersLoginBinding = ActivityUsersLoginBinding.inflate(layoutInflater)
        setContentView(activityUsersLoginBinding.root)
        mRealm = DatabaseService(this).realmInstance
        val selectedTeamId = intent.getStringExtra("selectedTeamId")

        activityUsersLoginBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        users = RealmMyTeam.getUsers(selectedTeamId, mRealm, "")

        mAdapter = TeamListAdapter(users as MutableList<RealmUserModel>, this, this)
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

    override fun onItemClick(user: RealmUserModel) {
        Glide.with(this)
            .load(user.userImage)
            .placeholder(R.drawable.profile)
            .error(R.drawable.profile)
            .into(activityUsersLoginBinding.userProfile)
        activityUsersLoginBinding.userNameTextView.text = user.name
    }
}