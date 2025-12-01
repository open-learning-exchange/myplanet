package org.ole.planet.myplanet.ui.userprofile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
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
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.BaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.callback.TableDataUpdate
import org.ole.planet.myplanet.databinding.FragmentAchievementBinding
import org.ole.planet.myplanet.databinding.LayoutButtonPrimaryBinding
import org.ole.planet.myplanet.databinding.RowAchievementBinding
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.sync.RealtimeSyncCoordinator
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.UrlUtils


private data class AchievementData(
    val goals: String = "",
    val purpose: String = "",
    val achievementsHeader: String = "",
    val achievements: List<String> = emptyList(),
    val achievementResources: List<RealmMyLibrary> = emptyList(),
    val references: List<String> = emptyList()
)
@AndroidEntryPoint
class AchievementFragment : BaseContainerFragment() {
    private var _binding: FragmentAchievementBinding? = null
    private val binding get() = _binding!!
    private lateinit var aRealm: Realm
    var user: RealmUserModel? = null
    var listener: OnHomeItemClickListener? = null
    private var achievementData: AchievementData? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    private val serverUrlMapper = ServerUrlMapper()
    
    @Inject
    lateinit var syncManager: SyncManager
    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    private lateinit var realtimeSyncListener: BaseRealtimeSyncListener
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
        _binding = FragmentAchievementBinding.inflate(inflater, container, false)
        aRealm = databaseService.realmInstance
        user = profileDbHandler.userModel
        binding.btnEdit.setOnClickListener {
            if (listener != null) listener?.openCallFragment(EditAchievementFragment())
        }
        return binding.root
    }

    override fun onDestroyView() {
        if (::realtimeSyncListener.isInitialized) {
            syncCoordinator.removeListener(realtimeSyncListener)
        }
        _binding = null
        super.onDestroyView()
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
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_achievements))
                        customProgressDialog?.show()
                    }
                }
            }

            override fun onSyncComplete() {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        refreshAchievementData()
                        prefManager.setAchievementsSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
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

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                achievementData = loadAchievementDataAsync()
                updateAchievementUI()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadAchievementDataAsync(): AchievementData = withContext(Dispatchers.IO) {
        databaseService.withRealm { realm ->
            val achievement = realm.where(RealmAchievement::class.java)
                .equalTo("_id", user?.id + "@" + user?.planetCode)
                .findFirst()

            if (achievement != null) {
                val achievementCopy = realm.copyFromRealm(achievement)
                val resourceIds = achievementCopy.achievements?.mapNotNull { json ->
                    GsonUtils.gson.fromJson(json, JsonObject::class.java)
                        ?.getAsJsonArray("resources")
                        ?.mapNotNull { it.asJsonObject?.get("_id")?.asString }
                }?.flatten()?.distinct()?.toTypedArray() ?: emptyArray()

                val resources = if (resourceIds.isNotEmpty()) {
                    realm.copyFromRealm(
                        realm.where(RealmMyLibrary::class.java)
                            .`in`("id", resourceIds)
                            .findAll()
                    )
                } else {
                    emptyList()
                }

                AchievementData(
                    goals = achievementCopy.goals ?: "",
                    purpose = achievementCopy.purpose ?: "",
                    achievementsHeader = achievementCopy.achievementsHeader ?: "",
                    achievements = achievementCopy.achievements ?: emptyList(),
                    achievementResources = resources,
                    references = achievementCopy.references ?: emptyList()
                )
            } else {
                AchievementData()
            }
        }
    }


    private fun updateAchievementUI() {
        achievementData?.let {
            setupAchievementHeader(it)
            populateAchievements(it)
            setupReferences(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRealtimeSync()
        setupUserData()
        loadInitialAchievementData()
    }

    private fun setupUserData() {
        binding.tvFirstName.text = user?.firstName
        binding.tvName.text =
            String.format("%s %s %s", user?.firstName, user?.middleName, user?.lastName)
    }

    private fun loadInitialAchievementData() {
        viewLifecycleOwner.lifecycleScope.launch {
            achievementData = loadAchievementDataAsync()
            updateAchievementUI()
        }
    }

    private fun setupRealtimeSync() {
        realtimeSyncListener = object : BaseRealtimeSyncListener() {
            override fun onTableDataUpdated(update: TableDataUpdate) {
                if (update.table == "achievements" && update.shouldRefreshUI) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        refreshAchievementData()
                    }
                }
            }
        }
        syncCoordinator.addListener(realtimeSyncListener)
    }

    private fun setupAchievementHeader(a: AchievementData) {
        binding.tvGoals.text = a.goals
        binding.tvPurpose.text = a.purpose
        binding.tvAchievementHeader.text = a.achievementsHeader
    }

    private fun populateAchievements(data: AchievementData) {
        binding.llAchievement.removeAllViews()
        val resourcesMap = data.achievementResources.mapNotNull { resource ->
            resource.id?.let { id -> id to resource }
        }.toMap()
        data.achievements.forEach { json ->
            val element = GsonUtils.gson.fromJson(json, JsonElement::class.java)
            val view = if (element is JsonObject) createAchievementView(element, resourcesMap) else null
            view?.let {
                if (it.parent != null) {
                    (it.parent as ViewGroup).removeView(it)
                }
                binding.llAchievement.addView(it)
            }
        }
    }

    private fun createAchievementView(ob: JsonObject, resourcesMap: Map<String, RealmMyLibrary>): View {
        val binding = RowAchievementBinding.inflate(LayoutInflater.from(requireContext()))
        val desc = getString("description", ob)
        binding.tvDescription.text = desc
        binding.tvDate.text = getString("date", ob)
        binding.tvTitle.text = getString("title", ob)

        val resourceIds = ob.getAsJsonArray("resources")?.mapNotNull {
            it.asJsonObject?.get("_id")?.asString
        } ?: emptyList()

        val libraries = resourceIds.mapNotNull { resourcesMap[it] }

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
        val btnBinding = LayoutButtonPrimaryBinding.inflate(LayoutInflater.from(requireContext()))
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
                startDownload(arrayListOf(UrlUtils.getUrl(lib)))
            }
        }
        return btnBinding.root
    }

    private fun setupReferences(data: AchievementData) {
        binding.rvOtherInfo.layoutManager = LinearLayoutManager(requireContext())
        val realmListReferences = RealmList<String>()
        realmListReferences.addAll(data.references)
        binding.rvOtherInfo.adapter = AdapterOtherInfo(requireContext(), realmListReferences)
    }


    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        if (this::aRealm.isInitialized && !aRealm.isClosed) {
            aRealm.close()
        }
        try {
            if (!mRealm.isClosed) {
                mRealm.close()
            }
        } catch (_: UninitializedPropertyAccessException) {
        }
        super.onDestroy()
    }
}
