package org.ole.planet.myplanet.ui.resources

import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
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
import org.ole.planet.myplanet.utilities.DialogUtils.getAlertDialog
import org.ole.planet.myplanet.utilities.FileUtils.getFileExtension
import org.ole.planet.myplanet.utilities.Utilities

class ResourceDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private lateinit var fragmentLibraryDetailBinding: FragmentLibraryDetailBinding
    private var libraryId: String? = null
    private lateinit var dbService: DatabaseService
    private lateinit var lRealm: Realm
    private lateinit var library: RealmMyLibrary
    var userModel: RealmUserModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            libraryId = requireArguments().getString("libraryId")
        }
    }

    override fun onDownloadComplete() {
        fragmentLibraryDetailBinding.btnDownload.setImageResource(R.drawable.ic_play)
        if (!library.userId?.contains(profileDbHandler.userModel?.id)!!) {
            if (!lRealm.isInTransaction) lRealm.beginTransaction()
            library.setUserId(profileDbHandler.userModel?.id)
            onAdd(lRealm, "resources", profileDbHandler.userModel?.id, libraryId)
            Utilities.toast(activity, getString(R.string.added_to_my_library))
            fragmentLibraryDetailBinding.btnRemove.setImageResource(R.drawable.close_x)
        }
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRatingView("resource", library.resourceId, library.title, this@ResourceDetailFragment)
        setLibraryData()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setLibraryData() {
        with(fragmentLibraryDetailBinding) {
            tvTitle.text = library.title
            timesRated.text = requireContext().getString(R.string.num_total, library.timesRated)
            setTextViewVisibility(tvAuthor, llAuthor, library.author)
            setTextViewVisibility(tvPublished, llPublisher, library.publisher)
            setTextViewVisibility(tvMedia, llMedia, library.mediaType)
            setTextViewVisibility(tvSubject, llSubject, library.subjectsAsString)
            setTextViewVisibility(tvLanguage, llLanguage, library.language)
            setTextViewVisibility(tvLicense, llLicense, library.linkToLicense)
            setTextViewVisibility(tvResource, llResource, listToString(library.resourceFor))
            setTextViewVisibility(tvType, llType, library.resourceType)
        }
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

    private fun setTextViewVisibility(textView: TextView, layout: View, text: String?) {
        if (!text.isNullOrEmpty()) {
            textView.text = text
            layout.visibility = View.VISIBLE
        } else {
            layout.visibility = View.GONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setClickListeners() {
        fragmentLibraryDetailBinding.btnDownload.setOnClickListener {
            if (TextUtils.isEmpty(library.resourceLocalAddress)) {
                Toast.makeText(activity, getString(R.string.link_not_available), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            openResource(library)
        }
        val isAdd = !library.userId?.contains(profileDbHandler.userModel?.id)!!
        if (userModel?.isGuest() != true) {
            fragmentLibraryDetailBinding.btnRemove.setImageResource(
                if (isAdd) {
                    R.drawable.ic_add_library
                } else {
                    R.drawable.close_x
                }
            )
            fragmentLibraryDetailBinding.btnRemove.contentDescription =
                if (isAdd) {
                    getString(R.string.add_to_mylib)
                } else {
                    getString(R.string.btn_remove_lib)
                }
        }
        else {
            fragmentLibraryDetailBinding.btnRemove.visibility = View.GONE
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
            Utilities.toast(activity, getString(R.string.resources) +" " + if (isAdd) getString(R.string.added_to) + getString(R.string.my_library) else getString(R.string.removed_from) + getString(R.string.my_library))
            setLibraryData()
        }
        fragmentLibraryDetailBinding.btnBack.setOnClickListener {
            val fragmentManager = parentFragmentManager
            fragmentManager.popBackStack()
        }
    }

    override fun onRatingChanged() {
        val `object` = getRatingsById(lRealm, "resource", library.resourceId, userModel?.id)
        setRatings(`object`)
    }
}
