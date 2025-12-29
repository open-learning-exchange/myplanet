package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentLibraryDetailBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.listToString
import org.ole.planet.myplanet.model.RealmRating.Companion.getRatingsById
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.FileUtils.getFileExtension
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class ResourceDetailFragment : BaseContainerFragment(), OnRatingChangeListener {
    private var _binding: FragmentLibraryDetailBinding? = null
    private val binding get() = _binding!!
    private var libraryId: String? = null
    private lateinit var library: RealmMyLibrary
    var userModel: RealmUserModel? = null
    private suspend fun fetchLibrary(libraryId: String): RealmMyLibrary? {
        return resourcesRepository.getLibraryItemById(libraryId)
            ?: resourcesRepository.getLibraryItemByResourceId(libraryId)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            libraryId = requireArguments().getString("libraryId")
        }
    }

    override fun onDownloadComplete() {
        super.onDownloadComplete()
        if (!::library.isInitialized) {
            return
        }
        val binding = _binding ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) {
                return@launch
            }
            val userId = profileDbHandler.userModel?.id
            try {
                val updatedLibrary = withContext(Dispatchers.IO) {
                    val backgroundLibrary = fetchLibrary(libraryId!!)
                    when {
                        backgroundLibrary == null -> null
                        backgroundLibrary.userId?.contains(userId) != true && userId != null ->
                            resourcesRepository.updateUserLibrary(libraryId!!, userId, true)
                        else -> backgroundLibrary
                    }
                }
                if (updatedLibrary != null) {
                    library = updatedLibrary
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            binding.btnDownload.setImageResource(R.drawable.ic_play)
            val currentUserId = profileDbHandler.userModel?.id
            if (currentUserId != null && library.userId?.contains(currentUserId) != true) {
                Utilities.toast(activity, getString(R.string.added_to_my_library))
                binding.btnRemove.setImageResource(R.drawable.close_x)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentLibraryDetailBinding.inflate(inflater, container, false)
        userModel = profileDbHandler.userModel
        setLoadingState(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            val id = libraryId
            if (id.isNullOrBlank()) {
                handleLibraryNotFound()
                return@launch
            }

            val fetchedLibrary = fetchLibrary(id)

            if (fetchedLibrary == null) {
                handleLibraryNotFound()
                return@launch
            }

            library = fetchedLibrary
            setLoadingState(false)
            initRatingView("resource", library.resourceId, library.title, this@ResourceDetailFragment)
            setLibraryData()
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnDownload.isEnabled = !isLoading
        binding.btnRemove.isEnabled = !isLoading
        val alpha = if (isLoading) 0.5f else 1f
        binding.btnDownload.alpha = alpha
        binding.btnRemove.alpha = alpha
    }

    private fun handleLibraryNotFound() {
        Toast.makeText(requireContext(), "Resource not found", Toast.LENGTH_LONG).show()
        NavigationHelper.popBackStack(parentFragmentManager)
    }

    private fun setLibraryData() {
        with(binding) {
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
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) {
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    profileDbHandler.setResourceOpenCount(library)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            try {
                onRatingChanged()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            setupDownloadButton()
            setClickListeners()
        }
    }

    private fun setupDownloadButton() {
        val isHtmlResource = library.mediaType == "HTML"
        val shouldShowButton = isHtmlResource || !TextUtils.isEmpty(library.resourceLocalAddress)

        binding.btnDownload.visibility = if (shouldShowButton) View.VISIBLE else View.GONE
        binding.btnDownload.setImageResource(
            if (!library.resourceOffline || library.isResourceOffline()) {
                R.drawable.ic_eye
            } else {
                R.drawable.ic_download
            })
        binding.btnDownload.contentDescription =
            if (!library.resourceOffline || library.isResourceOffline()) {
                getString(R.string.view)
            } else {
                getString(R.string.download)
            }
        if (getFileExtension(library.resourceLocalAddress) == "mp4") {
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
        binding.btnDownload.setOnClickListener {
            val isHtmlResource = library.mediaType == "HTML"
            if (!isHtmlResource && TextUtils.isEmpty(library.resourceLocalAddress)) {
                Toast.makeText(activity, getString(R.string.link_not_available), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            openResource(library)
        }
        val userId = profileDbHandler.userModel?.id
        val isAdd = userId?.let { library.userId?.contains(it) } != true
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
                    getString(R.string.remove)
                }
        } else {
            binding.btnRemove.visibility = View.GONE
        }
        binding.btnRemove.setOnClickListener {
            val userId = profileDbHandler.userModel?.id
            viewLifecycleOwner.lifecycleScope.launch {
                if (!isAdded) {
                    return@launch
                }
                val updatedLibrary = withContext(Dispatchers.IO) {
                    try {
                        if (userId != null) {
                            resourcesRepository.updateUserLibrary(libraryId!!, userId, isAdd)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                try {
                    if (updatedLibrary != null) {
                        library = updatedLibrary
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Utilities.toast(activity, getString(R.string.resources) + " " +
                        if (isAdd) getString(R.string.added_to_my_library)
                        else getString(R.string.removed_from_mylibrary))
                setLibraryData()
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

    private var lastKnownRating: com.google.gson.JsonObject? = null
    override fun onRatingChanged() {
        lastKnownRating?.let { setRatings(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded) return@launch
            try {
                withTimeout(2000) {
                    val rating = withContext(Dispatchers.IO) {
                        databaseService.withRealm { realm ->
                            getRatingsById(realm, "resource", library.resourceId, userModel?.id)
                        } as? com.google.gson.JsonObject
                    }
                    lastKnownRating = rating
                    setRatings(rating)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    override fun onDestroy() {
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
