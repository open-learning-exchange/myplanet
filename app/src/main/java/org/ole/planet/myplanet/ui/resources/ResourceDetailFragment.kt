package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentLibraryDetailBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.listToString
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatingsById
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onRemove
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.FileUtils.getFileExtension
import org.ole.planet.myplanet.utilities.Utilities

class ResourceDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private var _binding: FragmentLibraryDetailBinding? = null
    private val binding get() = _binding!!
    private var libraryId: String? = null
    private var library: RealmMyLibrary? = null
    var userModel: RealmUserModel? = null
    private val fragmentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            libraryId = requireArguments().getString("libraryId")
        }
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        fragmentScope.launch {
            val userId = withContext(Dispatchers.Main) {
                profileDbHandler.userModel?.id
            }
            withContext(Dispatchers.IO) {
                try {
                    databaseService.withRealm { backgroundRealm ->
                        val backgroundLibrary = backgroundRealm.where(RealmMyLibrary::class.java)
                            .equalTo("resourceId", libraryId).findFirst()
                        if (backgroundLibrary != null && backgroundLibrary.userId?.contains(userId) != true) {
                            backgroundRealm.executeTransaction {
                                backgroundLibrary.setUserId(userId)
                            }
                            onAdd(backgroundRealm, "resources", userId, libraryId)
                        }
                        library = backgroundLibrary?.let { backgroundRealm.copyFromRealm(it) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                library?.let {
                    binding.btnDownload.setImageResource(R.drawable.ic_play)
                    if (!it.userId?.contains(profileDbHandler.userModel?.id)!!) {
                        Utilities.toast(activity, getString(R.string.added_to_my_library))
                        binding.btnRemove.setImageResource(R.drawable.close_x)
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentLibraryDetailBinding.inflate(inflater, container, false)
        userModel = UserProfileDbHandler(requireContext()).userModel!!
        library = databaseService.withRealm { realm ->
            realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceId", libraryId)
                .findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val lib = library
        if (lib == null) {
            Utilities.toast(activity, getString(R.string.resource_not_found_message))
            val activity = requireActivity()
            if (activity is AddResourceActivity) {
                activity.finish()
            } else {
                NavigationHelper.popBackStack(parentFragmentManager)
            }
            return
        }
        initRatingView("resource", lib.resourceId, lib.title, this@ResourceDetailFragment)
        setLibraryData()
    }

    private fun setLibraryData() {
        val lib = library ?: return
        with(binding) {
            tvTitle.text = lib.title
            timesRated.text = requireContext().getString(R.string.num_total, lib.timesRated)
            setTextViewVisibility(tvAuthor, llAuthor, lib.author)
            setTextViewVisibility(tvPublished, llPublisher, lib.publisher)
            setTextViewVisibility(tvMedia, llMedia, lib.mediaType)
            setTextViewVisibility(tvSubject, llSubject, lib.subjectsAsString)
            setTextViewVisibility(tvLanguage, llLanguage, lib.language)
            setTextViewVisibility(tvLicense, llLicense, lib.linkToLicense)
            setTextViewVisibility(tvResource, llResource, listToString(lib.resourceFor))
            setTextViewVisibility(tvType, llType, lib.resourceType)
        }
        fragmentScope.launch {
            withContext(Dispatchers.Main) {
                try {
                    profileDbHandler.setResourceOpenCount(lib)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                try {
                    onRatingChanged()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
                setupDownloadButton()
                setClickListeners()
            }
        }
    }

    private fun setupDownloadButton() {
        val lib = library ?: return
        binding.btnDownload.visibility = if (TextUtils.isEmpty(lib.resourceLocalAddress)) View.GONE else View.VISIBLE
        binding.btnDownload.setImageResource(
            if (!lib.resourceOffline || lib.isResourceOffline()) {
                R.drawable.ic_eye
            } else {
                R.drawable.ic_download
            })
        binding.btnDownload.contentDescription =
            if (!lib.resourceOffline || lib.isResourceOffline()) {
                getString(R.string.view)
            } else {
                getString(R.string.download)
            }
        if (getFileExtension(lib.resourceLocalAddress) == "mp4") {
            binding.btnDownload.setImageResource(R.drawable.ic_play)
        }
    }

    private fun setTextViewVisibility(textView: TextView, layout: View, text: String?) {
        if (!text.isNullOrEmpty()) {
            textView.text = text
            layout.visibility = View.VISIBLE
        } else {
            layout.visibility = View.GONE
        }
    }

    private fun setClickListeners() {
        val lib = library ?: return
        binding.btnDownload.setOnClickListener {
            if (TextUtils.isEmpty(lib.resourceLocalAddress)) {
                Toast.makeText(activity, getString(R.string.link_not_available), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            openResource(lib)
        }
        val isAdd = !lib.userId?.contains(profileDbHandler.userModel?.id)!!
        if (userModel?.isGuest() != true) {
            binding.btnRemove.setImageResource(
                if (isAdd) {
                    R.drawable.ic_add_library
                } else {
                    R.drawable.close_x
                }
            )
            binding.btnRemove.contentDescription =
                if (isAdd) {
                    getString(R.string.add_to_mylib)
                } else {
                    getString(R.string.btn_remove_lib)
                }
        } else {
            binding.btnRemove.visibility = View.GONE
        }
        binding.btnRemove.setOnClickListener {
            val userId = profileDbHandler.userModel?.id
            fragmentScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        databaseService.withRealm { backgroundRealm ->
                            val backgroundLibrary = backgroundRealm.where(RealmMyLibrary::class.java)
                                .equalTo("resourceId", libraryId).findFirst()

                            if (backgroundLibrary != null) {
                                backgroundRealm.executeTransaction {
                                    if (isAdd) {
                                        backgroundLibrary.setUserId(userId)
                                    } else {
                                        backgroundLibrary.removeUserId(userId)
                                    }
                                }
                                if (isAdd) {
                                    onAdd(backgroundRealm, "resources", userId, libraryId)
                                } else {
                                    onRemove(backgroundRealm, "resources", userId, libraryId)
                                }
                                library = backgroundRealm.copyFromRealm(backgroundLibrary)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    Utilities.toast(activity, getString(R.string.resources) + " " +
                            if (isAdd) getString(R.string.added_to_my_library)
                            else getString(R.string.removed_from_mylibrary))
                    setLibraryData()
                }
            }
        }
        binding.btnBack.setOnClickListener {
            val activity = requireActivity()
            if (activity is AddResourceActivity) {
                activity.finish()
            } else {
                NavigationHelper.popBackStack(parentFragmentManager)
            }
        }
    }

    override fun onRatingChanged() {
        val lib = library ?: return
        val obj = databaseService.withRealm { realm ->
            getRatingsById(realm, "resource", lib.resourceId, userModel?.id)
        }
        setRatings(obj)
    }
    override fun onDestroy() {
        fragmentScope.cancel()
        try {
            if (!mRealm.isClosed) {
                mRealm.close()
            }
        } catch (_: UninitializedPropertyAccessException) {
        }
        super.onDestroy()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
