package org.ole.planet.myplanet.ui.library

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentLibraryDetailBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.listToString
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatingsById
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.FileUtils.getFileExtension
import org.ole.planet.myplanet.utilities.Utilities

class LibraryDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private lateinit var fragmentLibraryDetailBinding: FragmentLibraryDetailBinding
    private var libraryId: String? = null
    private lateinit var dbService: DatabaseService
    private lateinit var lRealm: Realm
    private lateinit var library: RealmMyLibrary
    var userModel: RealmUserModel? = null
    private var openFrom: String? = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            libraryId = requireArguments().getString("libraryId")
            if (requireArguments().containsKey("openFrom")) openFrom = requireArguments().getString("openFrom")
        }
    }

    override fun onDownloadComplete() {
        fragmentLibraryDetailBinding.btnDownload.setImageResource(R.drawable.ic_play)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        fragmentLibraryDetailBinding = FragmentLibraryDetailBinding.inflate(inflater, container, false)
        dbService = DatabaseService(requireActivity())
        lRealm = dbService.realmInstance
        userModel = UserProfileDbHandler(requireContext()).userModel!!
        library = lRealm.where(RealmMyLibrary::class.java).equalTo("resourceId", libraryId).findFirst()!!
        return fragmentLibraryDetailBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRatingView("resource", library.resourceId, library.title, this@LibraryDetailFragment)
        setLibraryData()
    }

    private fun setLibraryData() {
        fragmentLibraryDetailBinding.tvTitle.text = String.format("%s%s", if (openFrom?.isEmpty() == true) "" else "$openFrom-", library.title)
        fragmentLibraryDetailBinding.tvAuthor.text = library.author
        fragmentLibraryDetailBinding.tvPublished.text = library.publisher
        fragmentLibraryDetailBinding.tvMedia.text = library.mediaType
        fragmentLibraryDetailBinding.tvSubject.text = library.subjectsAsString
        fragmentLibraryDetailBinding.tvLanguage.text = library.language
        fragmentLibraryDetailBinding.tvLicense.text = library.linkToLicense
        fragmentLibraryDetailBinding.tvResource.text = listToString(library.resourceFor)
        profileDbHandler.setResourceOpenCount(library)
        try {
            onRatingChanged()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        fragmentLibraryDetailBinding.btnDownload.visibility = if (TextUtils.isEmpty(library.resourceLocalAddress)) View.GONE else View.VISIBLE
        fragmentLibraryDetailBinding.btnDownload.setImageResource(
            if (!library.resourceOffline || library.isResourceOffline()) {
                R.drawable.ic_eye
            } else {
                R.drawable.ic_download
            })
        fragmentLibraryDetailBinding.btnDownload.contentDescription =
            if (!library.resourceOffline || library.isResourceOffline()) {
                getString(R.string.view)
            } else {
                getString(R.string.download)
            }
        if (getFileExtension(library.resourceLocalAddress) == "mp4") {
            fragmentLibraryDetailBinding.btnDownload.setImageResource(R.drawable.ic_play)
        }
        setClickListeners()
    }

    private fun setClickListeners() {
        fragmentLibraryDetailBinding.btnDownload.setOnClickListener {
            if (TextUtils.isEmpty(library.resourceLocalAddress)) {
                Toast.makeText(activity, getString(R.string.link_not_available), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            openResource(library)
        }
        Utilities.log("user id " + profileDbHandler.userModel?.id + " " + library.userId?.contains(profileDbHandler.userModel?.id))
        val isAdd = !library.userId?.contains(profileDbHandler.userModel?.id)!!
        fragmentLibraryDetailBinding.btnRemove.setImageResource(
            if (isAdd) {
                R.drawable.ic_add_library
            } else {
                R.drawable.close_x
            })
        fragmentLibraryDetailBinding.btnRemove.contentDescription =
            if (isAdd) {
                getString(R.string.add_to_mylib)
            } else {
                getString(R.string.btn_remove_lib)
            }
        fragmentLibraryDetailBinding.btnRemove.setOnClickListener {
            if (!lRealm.isInTransaction) lRealm.beginTransaction()
            if (isAdd) {
                library.setUserId(profileDbHandler.userModel?.id)
                onAdd(lRealm, "resources", profileDbHandler.userModel?.id, libraryId)
            } else {
                library.removeUserId(profileDbHandler.userModel?.id)
                onRemove(lRealm, "resources", profileDbHandler.userModel?.id, libraryId)
            }
            Utilities.toast(activity, getString(R.string.resources) + if (isAdd) getString(R.string.added_to) else getString(R.string.removed_from) + getString(R.string.my_library))
            setLibraryData()
        }
        fragmentLibraryDetailBinding.btnBack.setOnClickListener { requireActivity().onBackPressed() }
    }

    override fun onRatingChanged() {
        val `object` = getRatingsById(lRealm, "resource", library.resourceId, userModel?.id)
        setRatings(`object`)
    }
}
