package org.ole.planet.myplanet.ui.team

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import io.realm.RealmResults
import org.json.JSONArray
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.databinding.AlertInputBinding
import org.ole.planet.myplanet.databinding.FragmentMyTeamsDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getResourceIds
import org.ole.planet.myplanet.model.RealmMyTeam.Companion.getUsers
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamLog.Companion.getVisitCount
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.courses.TakeCourseFragment
import org.ole.planet.myplanet.ui.resources.ResourceDetailFragment
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.userprofile.UserDetailFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

class MyTeamsDetailFragment : BaseNewsFragment() {
    private lateinit var fragmentMyTeamsDetailBinding: FragmentMyTeamsDetailBinding
    lateinit var tvDescription: TextView
    var user: RealmUserModel? = null
    var teamId: String? = null
    var team: RealmMyTeam? = null
    lateinit var listContent: ListView
    private lateinit var tabLayout: TabLayout
    lateinit var dbService: DatabaseService
    private lateinit var rvDiscussion: RecyclerView
    lateinit var llRv: LinearLayout
    private var isMyTeam = false
    private var libraries: RealmResults<RealmMyLibrary>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            teamId = requireArguments().getString("id")
            isMyTeam = requireArguments().getBoolean("isMyTeam", false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMyTeamsDetailBinding = FragmentMyTeamsDetailBinding.inflate(inflater, container, false)
        val v: View = fragmentMyTeamsDetailBinding.root
        initializeViews(v)
        dbService = DatabaseService(requireActivity())
        mRealm = dbService.realmInstance
        user = profileDbHandler.userModel?.let { mRealm.copyFromRealm(it) }
        team = mRealm.where(RealmMyTeam::class.java).equalTo("_id", teamId).findFirst()
        return fragmentMyTeamsDetailBinding.root
    }

    private fun initializeViews(v: View) {
        llRv = v.findViewById(R.id.ll_rv)
        rvDiscussion = v.findViewById(R.id.rv_discussion)
        tvDescription = v.findViewById(R.id.description)
        tabLayout = v.findViewById(R.id.tab_layout)
        listContent = v.findViewById(R.id.list_content)
        fragmentMyTeamsDetailBinding.btnInvite.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) {
            View.VISIBLE
        } else {
            View.GONE
        }
        fragmentMyTeamsDetailBinding.btnLeave.visibility = if (showBetaFeature(Constants.KEY_MEETUPS, requireContext())) {
            View.VISIBLE
        } else {
            View.GONE
        }
        v.findViewById<View>(R.id.add_message).setOnClickListener { showAddMessage() }
    }

    private fun showAddMessage() {
        val alertInputBinding = AlertInputBinding.inflate(layoutInflater)
        alertInputBinding.tlInput.hint = getString(R.string.enter_message)
        alertInputBinding.custMsg.text = getString(R.string.add_message)

        val dialog = AlertDialog.Builder(requireActivity(), R.style.CustomAlertDialog)
            .setView(alertInputBinding.root)
            .setPositiveButton(R.string.save) { _: DialogInterface?, _: Int ->
                val msg = "${alertInputBinding.tlInput.editText?.text}".trim { it <= ' ' }
                if (msg.isEmpty()) {
                    Utilities.toast(activity, R.string.message_is_required.toString())
                    return@setPositiveButton
                }
                val map = HashMap<String?, String>()
                map["viewableBy"] = "teams"
                map["viewableId"] = teamId!!
                map["message"] = msg
                map["messageType"] = team?.teamType!!
                map["messagePlanetCode"] = team?.teamPlanetCode!!
                createNews(map, mRealm, user, imageList)
                rvDiscussion.adapter?.notifyItemInserted(0)
            }.setNegativeButton(R.string.cancel, null).create()
        dialog.show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentMyTeamsDetailBinding.title.text = team?.name
        tvDescription.text = team?.description
        setTeamList()
    }

    override fun onNewsItemClick(news: RealmNews?) {}
    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    private fun setTeamList() {
        val users: List<RealmUserModel> = getUsers(teamId, mRealm, "")
        createTeamLog()
        val reqUsers = getRequestedTeamList(team?.requests)
        val realmNewsList: List<RealmNews> = mRealm.where(RealmNews::class.java)
            .isEmpty("replyTo").equalTo("viewableBy", "teams")
            .equalTo("viewableId", team?._id).findAll()
        rvDiscussion.layoutManager = LinearLayoutManager(activity)
        showRecyclerView(realmNewsList)
        listContent.visibility = View.GONE
        val courses = mRealm.where(RealmMyCourse::class.java).`in`("id", team?.courses?.toTypedArray<String>()).findAll()
        libraries = mRealm.where(RealmMyLibrary::class.java).`in`("id", getResourceIds(teamId, mRealm).toTypedArray<String>()).findAll()
        tabLayout.getTabAt(1)?.setText(String.format(getString(R.string.joined_members_colon) + " (%s)", users.size))
        tabLayout.getTabAt(3)?.setText(String.format(getString(R.string.courses_colon) + " (%s)", courses.size))
        tabLayout.getTabAt(2)?.setText(String.format(getString(R.string.requested_members_colon) + " (%s)", reqUsers.size))
        tabLayout.getTabAt(4)?.setText(String.format(getString(R.string.resources_colon) + " (%s)", libraries?.size))
        if (!isMyTeam) {
            try {
                (tabLayout.getChildAt(0) as ViewGroup).getChildAt(0).visibility = View.GONE
                (tabLayout.getChildAt(4) as ViewGroup).getChildAt(0).visibility = View.GONE
                tabLayout.getTabAt(1)?.select()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        setTabListener(users, courses, reqUsers)
    }

    private fun createTeamLog() {
        if (!mRealm.isInTransaction) {
            mRealm.beginTransaction()
        }
        val log = mRealm.createObject(RealmTeamLog::class.java, "${UUID.randomUUID()}")
        log.teamId = teamId
        log.user = user?.name
        log.createdOn = user?.planetCode
        log.type = "teamVisit"
        log.teamType = team?.teamType
        log.parentCode = user?.parentCode
        log.time = Date().time
        mRealm.commitTransaction()
    }

    private fun showRecyclerView(realmNewsList: List<RealmNews?>?) {
        val adapterNews = activity?.let { realmNewsList?.let { it1 ->
            AdapterNews(it, it1.toMutableList(), user, null, "")
        } }
        adapterNews?.setmRealm(mRealm)
        adapterNews?.setListener(this)
        rvDiscussion.adapter = adapterNews
        llRv.visibility = View.VISIBLE
    }

    private fun setTabListener(users: List<RealmUserModel>, courses: RealmResults<RealmMyCourse>, reqUsers: List<RealmUserModel>) {
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        listContent.visibility = View.GONE
                        llRv.visibility = View.VISIBLE
                    }
                    1 -> setListContent(tab, String.format(getString(R.string.joined_members_colon) + " (%s)", users.size), users)
                    2 -> setListContent(tab, String.format(getString(R.string.requested_members_colon) + " (%s)", reqUsers.size), reqUsers)
                    3 -> setCourseList(tab, courses)
                    4 -> setLibraryList(tab)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setLibraryList(tab: TabLayout.Tab) {
        hideRv(tab, String.format(getString(R.string.resources_colon) + " (%s)", libraries?.size))
        listContent.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, libraries!!)
        listContent.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
            if (homeItemClickListener != null) {
                val f = ResourceDetailFragment()
                val b = Bundle()
                b.putString("libraryId", libraries!![i]?.id)
                f.arguments = b
                homeItemClickListener?.openCallFragment(f)
            }
        }
    }

    private fun setCourseList(tab: TabLayout.Tab, courses: RealmResults<RealmMyCourse>) {
        hideRv(tab, String.format(getString(R.string.courses_colon) + " (%s)", courses.size))
        listContent.adapter = ArrayAdapter(requireActivity(), android.R.layout.simple_list_item_1, courses)
        listContent.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
            if (homeItemClickListener != null) {
                openFragment(courses[i]?.courseId, TakeCourseFragment())
            }
        }
    }

    private fun hideRv(tab: TabLayout.Tab, s: String) {
        listContent.visibility = View.VISIBLE
        llRv.visibility = View.GONE
        tab.setText(s)
    }

    private fun setListContent(tab: TabLayout.Tab, s: String, data: List<RealmUserModel>) {
        listContent.visibility = View.VISIBLE
        llRv.visibility = View.GONE
        tab.setText(s)
        listContent.adapter = object : ArrayAdapter<RealmUserModel?>(requireActivity(), android.R.layout.simple_list_item_1, data) {
            override fun getView(position: Int, viewConverted: View?, parent: ViewGroup): View {
                var convertView = viewConverted
                if (convertView == null) {
                    convertView = LayoutInflater.from(activity)
                        .inflate(android.R.layout.simple_list_item_1, parent, false)
                }
                val tv = convertView!!.findViewById<TextView>(android.R.id.text1)
                val formattedText = getString(R.string.visit_count, getItem(position)?.name ?: "", getVisitCount(mRealm, getItem(position)?.name, teamId), getString(R.string.visits))
                tv.text = formattedText
                return convertView
            }
        }
        listContent.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
            openFragment(data[i].id, UserDetailFragment())
        }
    }

    private fun openFragment(id: String?, f: Fragment) {
        val b = Bundle()
        b.putString("id", id)
        f.arguments = b
        homeItemClickListener?.openCallFragment(f)
    }

    private fun getRequestedTeamList(req: String?): List<RealmUserModel> {
        try {
            val array = JSONArray(req)
            val ids = arrayOfNulls<String>(array.length())
            for (i in 0 until array.length()) {
                ids[i] = array[i].toString()
            }
            return mRealm.where(RealmUserModel::class.java).`in`("id", ids).findAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ArrayList()
    }

    override fun setData(list: List<RealmNews?>?) {
        showRecyclerView(list)
    }
}
