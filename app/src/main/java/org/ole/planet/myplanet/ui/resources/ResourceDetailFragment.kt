package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
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
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.FileUtils.getFileExtension
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class ResourceDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private var _binding: FragmentLibraryDetailBinding? = null
    private val binding get() = _binding!!
    private var libraryId: String? = null
    private var library: RealmMyLibrary? = null
    var userModel: RealmUserModel? = null
    private val fragmentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private suspend fun fetchLibrary(libraryId: String): RealmMyLibrary? {
        return libraryRepository.getLibraryItemById(libraryId)
            ?: libraryRepository.getLibraryItemByResourceId(libraryId)
            ?: libraryRepository.getAllLibraryItems().firstOrNull { it.resourceId == libraryId }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            libraryId = requireArguments().getString("libraryId")
        }
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        val currentLibraryId = libraryId ?: return
        fragmentScope.launch {
            val userId = withContext(Dispatchers.Main) {
                profileDbHandler?.userModel?.id
            }
            withContext(Dispatchers.IO) {
                try {
                    val backgroundLibrary = fetchLibrary(currentLibraryId)
                    library = if (backgroundLibrary != null && userId != null && backgroundLibrary.userId?.contains(userId) != true) {
                        libraryRepository.updateUserLibrary(currentLibraryId, userId, true) ?: backgroundLibrary
                    } else {
                        backgroundLibrary
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                val currentBinding = _binding ?: return@withContext
                val updatedLibrary = library ?: return@withContext
                currentBinding.btnDownload.setImageResource(R.drawable.ic_play)
                val currentUserId = profileDbHandler?.userModel?.id
                if (currentUserId != null && updatedLibrary.userId?.contains(currentUserId) != true) {
                    Utilities.toast(activity, getString(R.string.added_to_my_library))
                    currentBinding.btnRemove.setImageResource(R.drawable.close_x)
                }
                setLibraryData()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentLibraryDetailBinding.inflate(inflater, container, false)
        userModel = UserProfileDbHandler(requireContext()).userModel!!
        setLoadingState(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentScope.launch {
            val fetchedLibrary = libraryId?.let { id ->
                withContext(Dispatchers.IO) { fetchLibrary(id) }
            }
            if (fetchedLibrary != null) {
                library = fetchedLibrary
                initRatingView("resource", fetchedLibrary.resourceId, fetchedLibrary.title, this@ResourceDetailFragment)
                setLibraryData()
                setLoadingState(false)
            } else {
                Utilities.toast(activity, getString(R.string.link_not_available))
                setLoadingState(true)
            }
        }
    }

    private fun setLibraryData() {
        val currentBinding = _binding ?: return
        val currentLibrary = library ?: return
        with(currentBinding) {
            tvTitle.text = currentLibrary.title
            timesRated.text = requireContext().getString(R.string.num_total, currentLibrary.timesRated)
            setTextViewVisibility(tvAuthor, llAuthor, currentLibrary.author)
            setTextViewVisibility(tvPublished, llPublisher, currentLibrary.publisher)
            setTextViewVisibility(tvMedia, llMedia, currentLibrary.mediaType)
            setTextViewVisibility(tvSubject, llSubject, currentLibrary.subjectsAsString)
            setTextViewVisibility(tvLanguage, llLanguage, currentLibrary.language)
            setTextViewVisibility(tvLicense, llLicense, currentLibrary.linkToLicense)
            setTextViewVisibility(tvResource, llResource, listToString(currentLibrary.resourceFor))
            setTextViewVisibility(tvType, llType, currentLibrary.resourceType)
        }
        fragmentScope.launch {
            withContext(Dispatchers.Main) {
                try {
                    profileDbHandler?.setResourceOpenCount(currentLibrary)
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
                setupDownloadButton(currentLibrary)
                setClickListeners()
            }
        }
    }

    private fun setupDownloadButton(currentLibrary: RealmMyLibrary) {
        val currentBinding = _binding ?: return
        currentBinding.btnDownload.visibility = if (TextUtils.isEmpty(currentLibrary.resourceLocalAddress)) View.GONE else View.VISIBLE
        currentBinding.btnDownload.setImageResource(
            if (!currentLibrary.resourceOffline || currentLibrary.isResourceOffline()) {
                R.drawable.ic_eye
            } else {
                R.drawable.ic_download
            })
        currentBinding.btnDownload.contentDescription =
            if (!currentLibrary.resourceOffline || currentLibrary.isResourceOffline()) {
                getString(R.string.view)
            } else {
                getString(R.string.download)
            }
        if (getFileExtension(currentLibrary.resourceLocalAddress) == "mp4") {
            currentBinding.btnDownload.setImageResource(R.drawable.ic_play)
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
        val currentBinding = _binding ?: return
        val currentLibrary = library ?: return
        currentBinding.btnDownload.setOnClickListener {
            val latestLibrary = library
            if (latestLibrary == null || TextUtils.isEmpty(latestLibrary.resourceLocalAddress)) {
                Toast.makeText(activity, getString(R.string.link_not_available), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            openResource(latestLibrary)
        }
        val userId = profileDbHandler?.userModel?.id
        val isAdd = userId?.let { currentLibrary.userId?.contains(it) } != true
        if (userModel?.isGuest() != true) {
            currentBinding.btnRemove.visibility = View.VISIBLE
            currentBinding.btnRemove.setImageResource(
                if (isAdd) {
                    R.drawable.ic_add_library
                } else {
                    R.drawable.close_x
                }
            )
            currentBinding.btnRemove.contentDescription =
                if (isAdd) {
                    getString(R.string.add_to_mylib)
                } else {
                    getString(R.string.btn_remove_lib)
                }
        } else {
            currentBinding.btnRemove.visibility = View.GONE
        }
        currentBinding.btnRemove.setOnClickListener {
            val userId = profileDbHandler?.userModel?.id
            val currentLibraryId = libraryId ?: return@setOnClickListener
            var updatedLibrary: RealmMyLibrary? = null
            fragmentScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        if (userId != null) {
                            updatedLibrary = libraryRepository.updateUserLibrary(currentLibraryId, userId, isAdd)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                withContext(Dispatchers.Main) {
                    updatedLibrary?.let { library = it }
                    Utilities.toast(activity, getString(R.string.resources) + " " +
                            if (isAdd) getString(R.string.added_to_my_library)
                            else getString(R.string.removed_from_mylibrary))
                    setLibraryData()
                }
            }
        }
        currentBinding.btnBack.setOnClickListener {
            val activity = requireActivity()
            if (activity is AddResourceActivity) {
                activity.finish()
            } else {
                NavigationHelper.popBackStack(parentFragmentManager)
            }
        }
    }

    override fun onRatingChanged() {
        val currentLibrary = library ?: return
        val `object` = databaseService.withRealm { realm ->
            getRatingsById(realm, "resource", currentLibrary.resourceId, userModel?.id)
        }
        setRatings(`object`)
    }

    private fun setLoadingState(isLoading: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.btnDownload.isEnabled = !isLoading
        currentBinding.btnRemove.isEnabled = !isLoading
        val alpha = if (isLoading) 0.5f else 1f
        currentBinding.btnDownload.alpha = alpha
        currentBinding.btnRemove.alpha = alpha
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
