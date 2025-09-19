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
import kotlinx.coroutines.runBlocking
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
    private lateinit var library: RealmMyLibrary
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
        fragmentScope.launch {
            val userId = withContext(Dispatchers.Main) {
                profileDbHandler?.userModel?.id
            }
            withContext(Dispatchers.IO) {
                try {
                    val backgroundLibrary = fetchLibrary(libraryId!!)
                    if (backgroundLibrary != null && backgroundLibrary.userId?.contains(userId) != true && userId != null) {
                        library = libraryRepository.updateUserLibrary(libraryId!!, userId, true)!!
                    } else if (backgroundLibrary != null) {
                        library = backgroundLibrary
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                binding.btnDownload.setImageResource(R.drawable.ic_play)
                val currentUserId = profileDbHandler?.userModel?.id
                if (currentUserId != null && library.userId?.contains(currentUserId) != true) {
                    Utilities.toast(activity, getString(R.string.added_to_my_library))
                    binding.btnRemove.setImageResource(R.drawable.close_x)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentLibraryDetailBinding.inflate(inflater, container, false)
        userModel = UserProfileDbHandler(requireContext()).userModel!!
        library = runBlocking {
            fetchLibrary(libraryId!!)
        }!!
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRatingView("resource", library.resourceId, library.title, this@ResourceDetailFragment)
        setLibraryData()
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
        fragmentScope.launch {
            withContext(Dispatchers.Main) {
                try {
                    profileDbHandler?.setResourceOpenCount(library)
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
        binding.btnDownload.visibility = if (TextUtils.isEmpty(library.resourceLocalAddress)) View.GONE else View.VISIBLE
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
            if (TextUtils.isEmpty(library.resourceLocalAddress)) {
                Toast.makeText(activity, getString(R.string.link_not_available), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            openResource(library)
        }
        val userId = profileDbHandler?.userModel?.id
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
                    getString(R.string.btn_remove_lib)
                }
        } else {
            binding.btnRemove.visibility = View.GONE
        }
        binding.btnRemove.setOnClickListener {
            val userId = profileDbHandler?.userModel?.id
            fragmentScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        if (userId != null) {
                            library = libraryRepository.updateUserLibrary(libraryId!!, userId, isAdd)!!
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
        val `object` = databaseService.withRealm { realm ->
            getRatingsById(realm, "resource", library.resourceId, userModel?.id)
        }
        setRatings(`object`)
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
