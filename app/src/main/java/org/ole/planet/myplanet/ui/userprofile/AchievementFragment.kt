package org.ole.planet.myplanet.ui.userprofile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.FragmentAchievementBinding
import org.ole.planet.myplanet.databinding.LayoutButtonPrimaryBinding
import org.ole.planet.myplanet.databinding.RowAchievementBinding
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class AchievementFragment : BaseContainerFragment() {
    private lateinit var fragmentAchievementBinding: FragmentAchievementBinding
    private lateinit var aRealm: Realm
    var user: RealmUserModel? = null
    var listener: OnHomeItemClickListener? = null
    private var achievement: RealmAchievement? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    private val serverUrlMapper = ServerUrlMapper()
    
    @Inject
    lateinit var syncManager: SyncManager
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

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
        aRealm = databaseService.realmInstance
        user = UserProfileDbHandler(MainApplication.context).userModel
        fragmentAchievementBinding.btnEdit.setOnClickListener {
            if (listener != null) listener?.openCallFragment(EditAchievementFragment())
        }
        return fragmentAchievementBinding.root
    }

    private fun startAchievementSync() {
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isAchievementsSynced()) {
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
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                activity?.runOnUiThread {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_achievements))
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

            override fun onSyncFailed(msg: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        Snackbar.make(fragmentAchievementBinding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                            .setAction("Retry") { startAchievementSync() }
                            .show()
                    }
                }
            }
        }, "full", listOf("achievements"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
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
            setupAchievementHeader(achievement!!)
            populateAchievements()
            setupReferences()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUserData()
        loadInitialAchievementData()
    }

    private fun setupUserData() {
        fragmentAchievementBinding.tvFirstName.text = user?.firstName
        fragmentAchievementBinding.tvName.text =
            String.format("%s %s %s", user?.firstName, user?.middleName, user?.lastName)
    }

    private fun loadInitialAchievementData() {
        achievement = aRealm.where(RealmAchievement::class.java)
            .equalTo("_id", user?.id + "@" + user?.planetCode)
            .findFirst()

        achievement?.let {
            updateAchievementUI()
            aRealm.addChangeListener {
                if (isAdded) {
                    populateAchievements()
                }
            }
        }
    }

    private fun setupAchievementHeader(a: RealmAchievement) {
        fragmentAchievementBinding.tvGoals.text = a.goals
        fragmentAchievementBinding.tvPurpose.text = a.purpose
        fragmentAchievementBinding.tvAchievementHeader.text = a.achievementsHeader
    }

    private fun populateAchievements() {
        fragmentAchievementBinding.llAchievement.removeAllViews()
        achievement?.achievements?.forEach { json ->
            val element = Gson().fromJson(json, JsonElement::class.java)
            val view = if (element is JsonObject) createAchievementView(element) else null
            view?.let {
                // Ensure the view is properly detached from any previous parent
                if (it.parent != null) {
                    (it.parent as ViewGroup).removeView(it)
                }
                fragmentAchievementBinding.llAchievement.addView(it)
            }
        }
    }

    private fun createAchievementView(ob: JsonObject): View {
        val binding = RowAchievementBinding.inflate(LayoutInflater.from(MainApplication.context))
        val desc = getString("description", ob)
        binding.tvDescription.text = desc
        binding.tvDate.text = getString("date", ob)
        binding.tvTitle.text = getString("title", ob)
        val libraries = getLibraries(ob.getAsJsonArray("resources"))

        if (desc.isNotEmpty() && libraries.isNotEmpty()) {
            binding.llRow.setOnClickListener { toggleDescription(binding) }
            binding.flexboxResources.removeAllViews()
            libraries.forEach { binding.flexboxResources.addView(createResourceButton(it)) }
        } else {
            binding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
        return binding.root
    }

    private fun toggleDescription(binding: RowAchievementBinding) {
        binding.llDesc.visibility = if (binding.llDesc.isGone) View.VISIBLE else View.GONE
        binding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(
            0,
            0,
            if (binding.llDesc.isGone) R.drawable.ic_down else R.drawable.ic_up,
            0
        )
    }

    private fun createResourceButton(lib: RealmMyLibrary): View {
        val btnBinding = LayoutButtonPrimaryBinding.inflate(LayoutInflater.from(MainApplication.context))
        btnBinding.root.text = lib.title
        btnBinding.root.setCompoundDrawablesWithIntrinsicBounds(
            0,
            0,
            if (lib.isResourceOffline()) R.drawable.ic_eye else R.drawable.ic_download,
            0
        )
        btnBinding.root.setOnClickListener {
            if (lib.isResourceOffline()) {
                openResource(lib)
            } else {
                startDownload(arrayListOf(Utilities.getUrl(lib)))
            }
        }
        return btnBinding.root
    }

    private fun setupReferences() {
        fragmentAchievementBinding.rvOtherInfo.layoutManager = LinearLayoutManager(MainApplication.context)
        fragmentAchievementBinding.rvOtherInfo.adapter =
            AdapterOtherInfo(MainApplication.context, achievement?.references ?: RealmList())
    }

    private fun getLibraries(array: JsonArray): List<RealmMyLibrary> {
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
