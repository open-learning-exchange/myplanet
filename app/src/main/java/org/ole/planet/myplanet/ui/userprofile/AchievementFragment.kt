package org.ole.planet.myplanet.ui.userprofile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentAchievementBinding
import org.ole.planet.myplanet.databinding.LayoutButtonPrimaryBinding
import org.ole.planet.myplanet.databinding.RowAchievementBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities
import androidx.core.view.isGone
import com.google.android.material.snackbar.Snackbar
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager

class AchievementFragment : BaseContainerFragment() {
    private lateinit var fragmentAchievementBinding: FragmentAchievementBinding
    private lateinit var rowAchievementBinding: RowAchievementBinding
    private lateinit var layoutButtonPrimaryBinding: LayoutButtonPrimaryBinding
    private lateinit var aRealm: Realm
    var user: RealmUserModel? = null
    var listener: OnHomeItemClickListener? = null
    private var achievement: RealmAchievement? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        startAchievementSync()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) listener = context
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentAchievementBinding = FragmentAchievementBinding.inflate(inflater, container, false)
        aRealm = DatabaseService(MainApplication.context).realmInstance
        user = UserProfileDbHandler(MainApplication.context).userModel
        fragmentAchievementBinding.btnEdit.setOnClickListener {
            if (listener != null) listener?.openCallFragment(EditAchievementFragment())
        }
        return fragmentAchievementBinding.root
    }

    private fun startAchievementSync() {
        if (!prefManager.isAchievementsSynced()) {
            SyncManager.instance?.start(object : SyncListener {
                override fun onSyncStarted() {
                    activity?.runOnUiThread {
                        if (isAdded && !requireActivity().isFinishing) {
                            customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                            customProgressDialog?.setText("Syncing achievements...")
                            customProgressDialog?.show()
                        }
                    }
                }

                override fun onSyncComplete() {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                            refreshAchievementData()
                            prefManager.setAchievementsSynced(true)
                        }
                    }
                }

                override fun onSyncFailed(message: String?) {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                            Snackbar.make(fragmentAchievementBinding.root, "Sync failed: ${message ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                                .setAction("Retry") { startAchievementSync() }
                                    .show()
                        }
                    }
                }
            }, "full", listOf("achievements"))
        }
    }

    private fun refreshAchievementData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            achievement = aRealm.where(RealmAchievement::class.java)
                .equalTo("_id", user?.id + "@" + user?.planetCode)
                .findFirst()

            updateAchievementUI()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAchievementUI() {
        if (achievement != null) {
            fragmentAchievementBinding.tvGoals.text = achievement?.goals
            fragmentAchievementBinding.tvPurpose.text = achievement?.purpose
            fragmentAchievementBinding.tvAchievementHeader.text = achievement?.achievementsHeader

            createAchievementList()

            fragmentAchievementBinding.rvOtherInfo.layoutManager = LinearLayoutManager(MainApplication.context)
            fragmentAchievementBinding.rvOtherInfo.adapter = AdapterOtherInfo(MainApplication.context, achievement?.references ?: RealmList())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentAchievementBinding.tvFirstName.text = user?.firstName
        fragmentAchievementBinding.tvName.text = String.format("%s %s %s", user?.firstName, user?.middleName, user?.lastName)
        loadInitialAchievementData()
    }

    private fun loadInitialAchievementData() {
        achievement = aRealm.where(RealmAchievement::class.java).equalTo("_id", user?.id + "@" + user?.planetCode).findFirst()

        if (achievement != null) {
            updateAchievementUI()

            aRealm.addChangeListener {
                if (isAdded) {
                    fragmentAchievementBinding.llAchievement.removeAllViews()
                    createAchievementList()
                }
            }
        }
    }

    private fun createAchievementList() {
        if (achievement?.achievements == null) return

        fragmentAchievementBinding.llAchievement.removeAllViews()
        for (s in achievement?.achievements!!) {
            rowAchievementBinding = RowAchievementBinding.inflate(LayoutInflater.from(MainApplication.context))
            val ob = Gson().fromJson(s, JsonElement::class.java)
            if (ob is JsonObject) {
                rowAchievementBinding.tvDescription.text = getString("description", ob.getAsJsonObject())
                rowAchievementBinding.tvDate.text = getString("date", ob.getAsJsonObject())
                rowAchievementBinding.tvTitle.text = getString("title", ob.getAsJsonObject())
                val libraries = getList(ob.getAsJsonArray("resources"))

                if (getString("description", ob.getAsJsonObject()).isNotEmpty() && libraries.size > 0) {
                    rowAchievementBinding.llRow.setOnClickListener {
                        rowAchievementBinding.llDesc.visibility = if (rowAchievementBinding.llDesc.isGone) View.VISIBLE else View.GONE
                        rowAchievementBinding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                            if (rowAchievementBinding.llDesc.isGone) R.drawable.ic_down else R.drawable.ic_up, 0
                        )
                    }

                    rowAchievementBinding.flexboxResources.removeAllViews()

                    for (lib in libraries) {
                        layoutButtonPrimaryBinding = LayoutButtonPrimaryBinding.inflate(LayoutInflater.from(MainApplication.context))
                        layoutButtonPrimaryBinding.root.text = lib.title
                        layoutButtonPrimaryBinding.root.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                            if (lib.isResourceOffline())
                                R.drawable.ic_eye
                            else
                                R.drawable.ic_download, 0)
                        layoutButtonPrimaryBinding.root.setOnClickListener {
                            if (lib.isResourceOffline()) {
                                openResource(lib)
                            } else {
                                val a = ArrayList<String>()
                                a.add(Utilities.getUrl(lib))
                                startDownload(a)
                            }
                        }
                        rowAchievementBinding.flexboxResources.addView(
                            layoutButtonPrimaryBinding.root
                        )
                    }
                } else {
                    rowAchievementBinding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            } else {
                rowAchievementBinding.root.visibility = View.GONE
            }

            if (rowAchievementBinding.root.parent != null) {
                (rowAchievementBinding.root.parent as ViewGroup).removeView(rowAchievementBinding.root)
            }
            fragmentAchievementBinding.llAchievement.addView(rowAchievementBinding.root)
        }
    }

    private fun getList(array: JsonArray): ArrayList<RealmMyLibrary> {
        val libraries = ArrayList<RealmMyLibrary>()
        for (e in array) {
            val id = e.asJsonObject["_id"].asString
            val li = aRealm.where(RealmMyLibrary::class.java).equalTo("id", id).findFirst()
            if (li != null) libraries.add(li)
        }
        return libraries
    }

    override fun onDestroy() {
        super.onDestroy()
        customProgressDialog?.dismiss()
        customProgressDialog = null
    }
}
