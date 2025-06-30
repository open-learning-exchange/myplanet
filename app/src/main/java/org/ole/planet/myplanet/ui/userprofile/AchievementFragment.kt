package org.ole.planet.myplanet.ui.userprofile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class AchievementFragment : BaseContainerFragment() {
    private lateinit var fragmentAchievementBinding: FragmentAchievementBinding
    private lateinit var aRealm: Realm
    var user: RealmUserModel? = null
    var listener: OnHomeItemClickListener? = null
    private var achievement: RealmAchievement? = null
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        achievement = aRealm.where(RealmAchievement::class.java)
            .equalTo("_id", user?.id + "@" + user?.planetCode).findFirst()
        setupUserData()
        achievement?.let {
            setupAchievementHeader(it)
            populateAchievements()
            setupReferences()
            aRealm.addChangeListener { populateAchievements() }
        }
    }

    private fun setupUserData() {
        fragmentAchievementBinding.tvFirstName.text = user?.firstName
        fragmentAchievementBinding.tvName.text =
            String.format("%s %s %s", user?.firstName, user?.middleName, user?.lastName)
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
            view?.let { fragmentAchievementBinding.llAchievement.addView(it) }
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
}
