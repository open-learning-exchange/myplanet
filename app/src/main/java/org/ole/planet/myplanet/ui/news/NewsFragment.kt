package org.ole.planet.myplanet.ui.news

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Trace
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.gson.JsonArray
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.databinding.FragmentNewsBinding
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNewsItem.OnNewsItemClickListener
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.textChanges

@AndroidEntryPoint
class NewsFragment : BaseNewsFragment(), OnNewsItemClickListener {
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!
    var user: RealmUserModel? = null
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var newsRepository: NewsRepository
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    private var filteredNewsList: List<NewsItem> = listOf()
    private var searchFilteredList: List<NewsItem> = listOf()
    private var labelFilteredList: List<NewsItem> = listOf()
    private lateinit var etSearch: EditText
    private var selectedLabel: String = "All"
    private val labelDisplayToValue = mutableMapOf<String, String>()

    private var adapterNewsItem: AdapterNewsItem? = null
    private lateinit var replyLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        replyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
             if (result.resultCode == Activity.RESULT_OK) {
                 val newsId = result.data?.getStringExtra("newsId")
                 newsId?.let { adapterNewsItem?.updateReplyBadge(it) }
                 adapterNewsItem?.refreshCurrentItems()
             }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        llImage = binding.llImages
        user = userProfileDbHandler.getUserModelCopy()
        setupUI(binding.newsFragmentParentLayout, requireActivity())
        if (user?.id?.startsWith("guest") == true) {
            binding.btnNewVoice.visibility = View.GONE
        }
        etSearch = binding.root.findViewById(R.id.et_search)
        binding.btnNewVoice.setOnClickListener {
            binding.llAddNews.visibility = if (binding.llAddNews.isVisible) {
                binding.etMessage.setText("")
                binding.tlMessage.error = null
                clearImages()
                View.GONE
            } else {
                View.VISIBLE
            }
            binding.btnNewVoice.text = if (binding.llAddNews.isVisible) {
                getString(R.string.hide_new_voice)
            } else {
                getString(R.string.new_voice)
            }
        }
        if (requireArguments().getBoolean("fromLogin")) {
            binding.btnNewVoice.visibility = View.GONE
            binding.llAddNews.visibility = View.GONE
        }

        if (mRealm.isInTransaction) {
            try {
                mRealm.commitTransaction()
            } catch (_: Exception) {
                mRealm.cancelTransaction()
            }
        }

        setupSearchTextListener()
        setupLabelFilter()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadData()
        binding.btnSubmit.setOnClickListener {
            val message = binding.etMessage.text.toString().trim { it <= ' ' }
            if (message.isEmpty()) {
                binding.tlMessage.error = getString(R.string.please_enter_message)
                return@setOnClickListener
            }
            binding.etMessage.setText(R.string.empty_text)
            val map = HashMap<String?, String>()
            map["message"] = message
            map["viewInId"] = "${user?.planetCode ?: ""}@${user?.parentCode ?: ""}"
            map["viewInSection"] = "community"
            map["messageType"] = "sync"
            map["messagePlanetCode"] = user?.planetCode ?: ""

            user?.let { it1 -> createNews(map, mRealm, it1, imageList) }
            imageList.clear()
            llImage?.removeAllViews()

            loadData()
            scrollToTop()
        }

        binding.addNewsImage.setOnClickListener {
            llImage = binding.llImages
            val openFolderIntent = FileUtils.openOleFolder(requireContext())
            openFolderLauncher.launch(openFolderIntent)
        }
    }

    override fun onDataChanged() {
        super.onDataChanged()
        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val news = withContext(Dispatchers.IO) {
                newsRepository.getCommunityNewsItems(getUserIdentifier())
            }
            filteredNewsList = news
            val labels = collectAllLabels(filteredNewsList)
            updateLabelSpinner(labels)
            labelFilteredList = applyLabelFilter(filteredNewsList)
            searchFilteredList = applySearchFilter(labelFilteredList)
            setData(searchFilteredList)
        }
    }

    private fun getUserIdentifier(): String {
        val defaultUserIdentifier = "${user?.planetCode ?: ""}@${user?.parentCode ?: ""}"
        if (defaultUserIdentifier.isNotEmpty() && defaultUserIdentifier != "@") {
            return defaultUserIdentifier
        }
        val planetCode = settings?.getString("planetCode", "") ?: ""
        val parentCode = settings?.getString("parentCode", "") ?: ""
        return "$planetCode@$parentCode"
    }

    override fun setData(list: List<RealmNews?>?) {
        // Legacy override - do nothing
    }

    fun setData(list: List<NewsItem>?) {
        if (!isAdded || list == null) return

        if (binding.rvNews.adapter == null) {
            changeLayoutManager(resources.configuration.orientation, binding.rvNews)
            val resourceIds = mutableSetOf<String>()
            list.forEach { news ->
                news.imagesArray.forEach { ob ->
                    val resourceId = getString("resourceId", ob)
                    if (!resourceId.isNullOrBlank()) {
                        resourceIds.add(resourceId)
                    }
                }
            }
            viewLifecycleOwner.lifecycleScope.launch {
                if (resourceIds.isNotEmpty()) {
                    val libraries = libraryRepository.getLibraryItemsByIds(resourceIds)
                    getUrlsAndStartDownload(
                        libraries.map<RealmMyLibrary, RealmMyLibrary?> { it },
                        arrayListOf()
                    )
                }
            }
            adapterNewsItem = AdapterNewsItem(requireActivity(), user, null)
            adapterNewsItem?.sharedPrefManager = sharedPrefManager
            adapterNewsItem?.setFromLogin(requireArguments().getBoolean("fromLogin"))
            adapterNewsItem?.setListener(this)
            adapterNewsItem?.registerAdapterDataObserver(observer)
            adapterNewsItem?.updateList(list)
            binding.rvNews.adapter = adapterNewsItem
        } else {
            adapterNewsItem?.updateList(list)
        }
        adapterNewsItem?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        binding.llAddNews.visibility = View.GONE
        binding.btnNewVoice.text = getString(R.string.new_voice)
    }

    override fun onNewsItemClick(news: NewsItem?) {
        val fromLogin = arguments?.getBoolean("fromLogin")
        if (fromLogin == false) {
            val bundle = Bundle()
            bundle.putString("newsId", news?.newsId)
            bundle.putString("newsRev", news?.newsRev)
            bundle.putString("conversations", news?.conversations)

            val chatDetailFragment = ChatDetailFragment()
            chatDetailFragment.arguments = bundle

            NavigationHelper.replaceFragment(
                parentFragmentManager,
                R.id.fragment_container,
                chatDetailFragment,
                addToBackStack = true
            )
        }
    }

    override fun showReply(news: NewsItem?, fromLogin: Boolean, nonTeamMember: Boolean) {
        if (news != null) {
            val intent = Intent(activity, ReplyActivity::class.java).putExtra("id", news.id)
                .putExtra("fromLogin", fromLogin)
                .putExtra("nonTeamMember", nonTeamMember)
            replyLauncher.launch(intent)
        }
    }

    override fun clearImages() {
        imageList.clear()
        llImage?.removeAllViews()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig.orientation
        changeLayoutManager(orientation, binding.rvNews)
    }

    private fun scrollToTop() {
        binding.rvNews.post {
            binding.rvNews.scrollToPosition(0)
        }
    }

    private val observer: AdapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            adapterNewsItem?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            adapterNewsItem?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            adapterNewsItem?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        }
    }
    
    private fun setupSearchTextListener() {
        etSearch.textChanges()
            .debounce(300)
            .onEach { text ->
                val searchQuery = text.toString().trim()
                searchFilteredList = applySearchFilter(labelFilteredList, searchQuery)
                setData(searchFilteredList)
                scrollToTop()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }
    
    private fun applySearchFilter(list: List<NewsItem>, queryParam: String? = null): List<NewsItem> {
        val query = queryParam ?: etSearch.text.toString().trim()
        
        if (query.isEmpty()) {
            return list
        }
        
        val filtered = list.filter { news ->
            val message = news.message?.trim() ?: ""
            val matches = message.contains(query, ignoreCase = true)
            matches
        }
        return filtered
    }
    
    private fun setupLabelFilter(precomputedLabels: List<String>? = null) {
        updateLabelSpinner(precomputedLabels)

        binding.filterByLabel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val labels = (binding.filterByLabel.adapter as ArrayAdapter<String>)
                selectedLabel = labels.getItem(position) ?: "All"
                labelFilteredList = applyLabelFilter(filteredNewsList)
                searchFilteredList = applySearchFilter(labelFilteredList)
                setData(searchFilteredList)
                scrollToTop()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun updateLabelSpinner(precomputedLabels: List<String>? = null) {
        val binding = _binding ?: return
        val labels = precomputedLabels ?: collectAllLabels(filteredNewsList)
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(requireContext(), R.style.ResourcePopupMenu)
        val adapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.filterByLabel.adapter = adapter

        val position = labels.indexOf(selectedLabel)
        if (position >= 0) {
            binding.filterByLabel.setSelection(position)
        } else {
            selectedLabel = "All"
            binding.filterByLabel.setSelection(0)
        }
    }
    
    private fun collectAllLabels(list: List<NewsItem>): List<String> {
        labelDisplayToValue.clear()

        val allLabels = mutableSetOf<String>()
        allLabels.add("All")

        Constants.LABELS.forEach { (labelName, labelValue) ->
            allLabels.add(labelName)
            labelDisplayToValue[labelName] = labelValue
        }

        allLabels.add("Shared Chat")

        list.forEach { news ->
            if (!news.viewIn.isNullOrEmpty()) {
                try {
                    val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                    if (ar.size() > 1) {
                        val ob = ar[0].asJsonObject
                        if (ob.has("name") && !ob.get("name").isJsonNull) {
                            val sharedTeamName = ob.get("name").asString
                            if (sharedTeamName.isNotEmpty()) {
                                allLabels.add(sharedTeamName)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            news.labels?.forEach { label ->
                val labelName = Constants.LABELS.entries.find { it.value == label }?.key
                    ?: org.ole.planet.myplanet.ui.news.NewsLabelManager.formatLabelValue(label)
                allLabels.add(labelName)
                labelDisplayToValue.putIfAbsent(labelName, label)
            }
        }

        return allLabels.sorted()
    }
    
    private fun applyLabelFilter(list: List<NewsItem>): List<NewsItem> {
        if (selectedLabel == "All") {
            return list
        }
        
        return list.filter { news ->
            when {
                selectedLabel == "Shared Chat" -> {
                    news.chat || news.viewableBy.equals("community", ignoreCase = true)
                }
                labelDisplayToValue.containsKey(selectedLabel) -> {
                    val labelValue = labelDisplayToValue[selectedLabel]
                    news.labels?.contains(labelValue) == true
                }
                else -> {
                    extractSharedTeamName(news) == selectedLabel
                }
            }
        }
    }
    
    private fun extractSharedTeamName(news: NewsItem?): String {
        if (!news?.viewIn.isNullOrEmpty()) {
            try {
                val ar = GsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)
                if (ar.size() > 1) {
                    val ob = ar[0].asJsonObject
                    if (ob.has("name") && !ob.get("name").isJsonNull) {
                        return ob.get("name").asString
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ""
    }

    // Implement AdapterNewsItem.OnNewsItemClickListener methods
    // ... onDelete, onEdit, onReply, onShare, onAddLabel, onRemoveLabel, onMemberSelected ...
    // These were added to BaseNewsFragment in my failed attempt. Now I must implement them here OR in BaseNewsFragment.
    // If I revert BaseNewsFragment, I lose them.
    // So I implement them here.

    override fun onMemberSelected(news: NewsItem) {
        if (!isAdded) return
        val userId = news.userId
        if (userId != null) {
            val userModel = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            val handler = profileDbHandler
            val fragment = org.ole.planet.myplanet.ui.news.NewsActions.showMemberDetails(userModel, handler) ?: return
            NavigationHelper.replaceFragment(
                requireActivity().supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
    }

    override fun onDelete(news: NewsItem) {
        val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
        if(realmNews != null) {
             org.ole.planet.myplanet.ui.news.NewsActions.deletePost(mRealm, realmNews, mutableListOf(), "", null)
             // listener null because I manually refresh
             onDataChanged()
        }
    }

    override fun onEdit(news: NewsItem, holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
         val user = profileDbHandler.userModel
         org.ole.planet.myplanet.ui.news.NewsActions.showEditAlert(requireContext(), mRealm, news.id, true, user, null, holder) { _, _, _ ->
             adapterNewsItem?.updateReplyBadge(news.id)
         }
    }

    override fun onReply(news: NewsItem, holder: androidx.recyclerview.widget.RecyclerView.ViewHolder) {
         val user = profileDbHandler.userModel
         org.ole.planet.myplanet.ui.news.NewsActions.showEditAlert(requireContext(), mRealm, news.id, false, user, null, holder) { _, _, _ ->
             adapterNewsItem?.updateReplyBadge(news.id)
         }
    }

    override fun onShare(news: NewsItem) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.share_with_community)
            .setMessage(R.string.confirm_share_community)
            .setPositiveButton(R.string.yes) { _, _ ->
                val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
                if (realmNews != null) {
                    val array = GsonUtils.gson.fromJson(realmNews.viewIn, JsonArray::class.java)
                    val firstElement = array.get(0)
                    val obj = firstElement.asJsonObject
                    if (!obj.has("name")) {
                        obj.addProperty("name", "")
                    }
                    val ob = com.google.gson.JsonObject()
                    ob.addProperty("section", "community")
                    val user = profileDbHandler.userModel
                    ob.addProperty("_id", user?.planetCode + "@" + user?.parentCode)
                    ob.addProperty("sharedDate", java.util.Calendar.getInstance().timeInMillis)
                    array.add(ob)

                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    realmNews.sharedBy = user?.id
                    realmNews.viewIn = GsonUtils.gson.toJson(array)
                    mRealm.commitTransaction()
                    org.ole.planet.myplanet.utilities.Utilities.toast(context, getString(R.string.shared_to_community))
                    onDataChanged()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onAddLabel(news: NewsItem, label: String) {
        val newsId = news.id
        mRealm.executeTransactionAsync({ transactionRealm ->
             val managedNews = transactionRealm.where(RealmNews::class.java)
                 .equalTo("id", newsId)
                 .findFirst()
             if (managedNews != null) {
                 var managedLabels = managedNews.labels
                 if (managedLabels == null) {
                     managedLabels = io.realm.RealmList()
                     managedNews.labels = managedLabels
                 }
                 if (!managedLabels.contains(label)) {
                     managedLabels.add(label)
                 }
             }
        }, {
             org.ole.planet.myplanet.utilities.Utilities.toast(context, getString(R.string.label_added))
             onDataChanged()
        })
    }

    override fun onRemoveLabel(news: NewsItem, label: String) {
        val newsId = news.id
        mRealm.executeTransactionAsync({ transactionRealm ->
             val managedNews = transactionRealm.where(RealmNews::class.java)
                 .equalTo("id", newsId)
                 .findFirst()
             managedNews?.labels?.remove(label)
        }, {
             onDataChanged()
        })
    }

    override fun getCurrentImageList(): io.realm.RealmList<String>? {
        return if (::imageList.isInitialized) imageList else null
    }

    override fun onDestroyView() {
        adapterNewsItem?.unregisterAdapterDataObserver(observer)
        if (isRealmInitialized()) {
            mRealm.close()
        }
        _binding = null
        super.onDestroyView()
    }
}
