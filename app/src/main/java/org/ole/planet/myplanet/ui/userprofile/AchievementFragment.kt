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
    val goals: String,
    val purpose: String,
    val achievementsHeader: String,
    val achievements: List<AchievementItem>,
    val references: List<String>,
)

private data class AchievementItem(
    val description: String,
    val date: String,
    val title: String,
    val resources: List<ResourceItem>,
)

private data class ResourceItem(
    val id: String,
    val title: String,
    val isOffline: Boolean,
)
@AndroidEntryPoint
class AchievementFragment : BaseContainerFragment() {
    private var _binding: FragmentAchievementBinding? = null
    private val binding get() = _binding!!
    private lateinit var aRealm: Realm
    private lateinit var realmChangeListener: io.realm.RealmChangeListener<Realm>
    var user: RealmUserModel? = null
    var listener: OnHomeItemClickListener? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    private val serverUrlMapper = ServerUrlMapper()
    
    @Inject
    lateinit var syncManager: SyncManager
    private val syncCoordinator = RealtimeSyncCoordinator.getInstance()
    private lateinit var realtimeSyncListener: BaseRealtimeSyncListener
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""
    private suspend fun loadAndProcessAchievementData(): AchievementData? {
        return withContext(Dispatchers.IO) {
            databaseService.withRealm { realm ->
                val achievement = realm.where(RealmAchievement::class.java)
                    .equalTo("_id", user?.id + "@" + user?.planetCode)
                    .findFirst()

                if (achievement == null) {
                    return@withRealm null
                }
                val achievementUnmanaged = realm.copyFromRealm(achievement)
                val allResourceIds = achievementUnmanaged.achievements.flatMap { json ->
                    GsonUtils.gson.fromJson(json, JsonObject::class.java)
                        .getAsJsonArray("resources")
                        .map { it.asJsonObject["_id"].asString }
                }.distinct()

                val resourcesMap = realm.where(RealmMyLibrary::class.java)
                    .`in`("id", allResourceIds.toTypedArray())
                    .findAll()
                    .associateBy { it.id }

                val achievementItems = achievementUnmanaged.achievements.map { json ->
                    val ob = GsonUtils.gson.fromJson(json, JsonObject::class.java)
                    val resourceItems = ob.getAsJsonArray("resources").mapNotNull { res ->
                        val resId = res.asJsonObject["_id"].asString
                        resourcesMap[resId]?.let { lib ->
                            ResourceItem(
                                id = lib.id,
                                title = lib.title,
                                isOffline = lib.isResourceOffline()
                            )
                        }
                    }
                    AchievementItem(
                        description = getString("description", ob),
                        date = getString("date", ob),
                        title = getString("title", ob),
                        resources = resourceItems
                    )
                }

                AchievementData(
                    goals = achievementUnmanaged.goals,
                    purpose = achievementUnmanaged.purpose,
                    achievementsHeader = achievementUnmanaged.achievementsHeader,
                    achievements = achievementItems,
                    references = achievementUnmanaged.references.map { it }
                )
            }
        }
    }

    private fun render(data: AchievementData?) {
        if (data == null) {
            return
        }
        binding.tvGoals.text = data.goals
        binding.tvPurpose.text = data.purpose
        binding.tvAchievementHeader.text = data.achievementsHeader

        binding.llAchievement.removeAllViews()
        data.achievements.forEach { achievement ->
            val view = createAchievementView(achievement)
            if (view.parent != null) {
                (view.parent as ViewGroup).removeView(view)
            }
            binding.llAchievement.addView(view)
        }

        binding.rvOtherInfo.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOtherInfo.adapter = AdapterOtherInfo(requireContext(), RealmList(*data.references.toTypedArray()))
    }
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
        if (::realmChangeListener.isInitialized) {
            aRealm.removeChangeListener(realmChangeListener)
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
        loadData()
    }

    private fun loadData() {
        if (!isAdded || requireActivity().isFinishing) return
        lifecycleScope.launch {
            val data = loadAndProcessAchievementData()
            if (isAdded && !requireActivity().isFinishing) {
                render(data)
            }
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
        loadData()
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

    private fun createAchievementView(achievement: AchievementItem): View {
        val binding = RowAchievementBinding.inflate(LayoutInflater.from(requireContext()))
        binding.tvDescription.text = achievement.description
        binding.tvDate.text = achievement.date
        binding.tvTitle.text = achievement.title

        if (achievement.description.isNotEmpty() && achievement.resources.isNotEmpty()) {
            binding.llRow.setOnClickListener { toggleDescription(binding) }
            binding.flexboxResources.removeAllViews()
            achievement.resources.forEach { resource ->
                binding.flexboxResources.addView(createResourceButton(resource))
            }
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

    private fun createResourceButton(resource: ResourceItem): View {
        val btnBinding = LayoutButtonPrimaryBinding.inflate(LayoutInflater.from(requireContext()))
        btnBinding.root.text = resource.title
        btnBinding.root.setCompoundDrawablesWithIntrinsicBounds(
            0,
            0,
            if (resource.isOffline) R.drawable.ic_eye else R.drawable.ic_download,
            0
        )
        btnBinding.root.setOnClickListener {
            if (resource.isOffline) {
                // Find the Realm object to open it. This is a quick operation.
                databaseService.withRealm { realm ->
                    val lib = realm.where(RealmMyLibrary::class.java)
                        .equalTo("id", resource.id)
                        .findFirst()
                    lib?.let { openResource(it) }
                }
            } else {
                databaseService.withRealm { realm ->
                    val lib = realm.where(RealmMyLibrary::class.java)
                        .equalTo("id", resource.id)
                        .findFirst()
                    lib?.let { startDownload(arrayListOf(UrlUtils.getUrl(it))) }
                }
            }
        }
        return btnBinding.root
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
