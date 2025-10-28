package org.ole.planet.myplanet.ui.news

import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.gson.Gson
import com.google.gson.JsonArray
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Case
import io.realm.RealmResults
import io.realm.Sort
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.databinding.FragmentNewsBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI

@AndroidEntryPoint
class NewsFragment : BaseNewsFragment() {
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!
    var user: RealmUserModel? = null
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var newsRepository: NewsRepository
    private var updatedNewsList: RealmResults<RealmNews>? = null
    private var filteredNewsList: List<RealmNews?> = listOf()
    private var searchFilteredList: List<RealmNews?> = listOf()
    private var labelFilteredList: List<RealmNews?> = listOf()
    private val gson = Gson()
    private lateinit var etSearch: EditText
    private var selectedLabel: String = "All"
    private val labelDisplayToValue = mutableMapOf<String, String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        llImage = binding.llImages
        user = userProfileDbHandler.userModel
        setupUI(binding.newsFragmentParentLayout, requireActivity())
        if (user?.id?.startsWith("guest") == true) {
            binding.btnNewVoice.visibility = View.GONE
        }
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

        updatedNewsList = mRealm.where(RealmNews::class.java).sort("time", Sort.DESCENDING)
            .isEmpty("replyTo").equalTo("docType", "message", Case.INSENSITIVE)
            .findAllAsync()

        updatedNewsList?.addChangeListener { results ->
            if (_binding == null) return@addChangeListener
            filteredNewsList = filterNewsList(results)
            updateLabelSpinner()
            labelFilteredList = applyLabelFilter(filteredNewsList)
            searchFilteredList = applySearchFilter(labelFilteredList)
            setData(searchFilteredList)
            scrollToTop()
        }
        
        etSearch = binding.root.findViewById(R.id.et_search)
        setupSearchTextListener()
        setupLabelFilter()
        
        return binding.root
    }

    private fun filterNewsList(results: RealmResults<RealmNews>): List<RealmNews?> {
        val filteredList: MutableList<RealmNews?> = ArrayList()
        for (news in results) {
            if (news.viewableBy.equals("community", ignoreCase = true)) {
                filteredList.add(news)
                continue
            }

            if (!news.viewIn.isNullOrEmpty()) {
                val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
                for (e in ar) {
                    val ob = e.asJsonObject
                    var userId = "${user?.planetCode}@${user?.parentCode}"
                    if(userId.isEmpty() || userId=="@"){
                        userId = settings?.getString("planetCode","")+"@"+settings?.getString("parentCode", "")
                    }
                    if (ob != null && ob.has("_id") && ob["_id"].asString.equals(userId, ignoreCase = true)) {
                        filteredList.add(news)
                        break
                    }
                }
            }
        }
        return filteredList
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCommunityNews()
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

            val n = user?.let { it1 -> createNews(map, mRealm, it1, imageList) }
            imageList.clear()
            llImage?.removeAllViews()
            adapterNews?.addItem(n)
            filteredNewsList = filterNewsList(updatedNewsList!!)
            labelFilteredList = applyLabelFilter(filteredNewsList)
            searchFilteredList = applySearchFilter(labelFilteredList)
            setData(searchFilteredList)
            scrollToTop()
        }

        binding.addNewsImage.setOnClickListener {
            llImage = binding.llImages
            val openFolderIntent = FileUtils.openOleFolder(requireContext())
            openFolderLauncher.launch(openFolderIntent)
        }
    }

    private fun loadCommunityNews() {
        viewLifecycleOwner.lifecycleScope.launch {
            val news = newsRepository.getCommunityVisibleNews(getUserIdentifier())
            filteredNewsList = news.map { it as RealmNews? }
            setupLabelFilter()
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
        if (!isAdded || list == null) return

        if (binding.rvNews.adapter == null) {
            changeLayoutManager(resources.configuration.orientation, binding.rvNews)
            val resourceIds = mutableSetOf<String>()
            list.forEach { news ->
                if ((news?.imagesArray?.size() ?: 0) > 0) {
                    val ob = news?.imagesArray?.get(0)?.asJsonObject
                    val resourceId = getString("resourceId", ob?.asJsonObject)
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
            val updatedListAsMutable: MutableList<RealmNews?> = list.toMutableList()
            val sortedList = updatedListAsMutable.sortedWith(compareByDescending { news ->
                getSortDate(news)
            })
            adapterNews = AdapterNews(requireActivity(), user, null, "", null, userProfileDbHandler)

            adapterNews?.setmRealm(mRealm)
            adapterNews?.setFromLogin(requireArguments().getBoolean("fromLogin"))
            adapterNews?.setListener(this)
            adapterNews?.registerAdapterDataObserver(observer)
            adapterNews?.updateList(sortedList)
            binding.rvNews.adapter = adapterNews
        } else {
            (binding.rvNews.adapter as? AdapterNews)?.updateList(list)
        }
        adapterNews?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        binding.llAddNews.visibility = View.GONE
        binding.btnNewVoice.text = getString(R.string.new_voice)
    }

    override fun onNewsItemClick(news: RealmNews?) {
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
            adapterNews?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            adapterNews?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            adapterNews?.let { showNoData(binding.tvMessage, it.itemCount, "news") }
        }
    }
    private fun getSortDate(news: RealmNews?): Long {
        if (news == null) return 0
        try {
            if (!news.viewIn.isNullOrEmpty()) {
                val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
                for (elem in ar) {
                    val obj = elem.asJsonObject
                    if (obj.has("section") && obj.get("section").asString.equals("community", true) && obj.has("sharedDate")) {
                        return obj.get("sharedDate").asLong
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return news.time
    }
    
    private fun setupSearchTextListener() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                searchFilteredList = applySearchFilter(labelFilteredList)
                setData(searchFilteredList)
                scrollToTop()
            }
            override fun afterTextChanged(s: Editable) {}
        })
    }
    
    private fun applySearchFilter(list: List<RealmNews?>): List<RealmNews?> {
        val query = etSearch.text.toString().trim()
        
        if (query.isEmpty()) {
            return list
        }
        
        val filtered = list.filter { news ->
            val message = news?.message?.trim() ?: ""
            val matches = message.contains(query, ignoreCase = true)
            matches
        }
        return filtered
    }
    
    private fun setupLabelFilter() {
        updateLabelSpinner()

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
    
    private fun updateLabelSpinner() {
        val binding = _binding ?: return
        val labels = collectAllLabels(filteredNewsList)
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
    
    private fun collectAllLabels(list: List<RealmNews?>): List<String> {
        labelDisplayToValue.clear()

        val allLabels = mutableSetOf<String>()
        allLabels.add("All")

        Constants.LABELS.forEach { (labelName, labelValue) ->
            allLabels.add(labelName)
            labelDisplayToValue[labelName] = labelValue
        }

        allLabels.add("Shared Chat")

        list.forEach { news ->
            if (!news?.viewIn.isNullOrEmpty()) {
                try {
                    val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
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

            news?.labels?.forEach { label ->
                val labelName = Constants.LABELS.entries.find { it.value == label }?.key
                    ?: NewsLabelManager.formatLabelValue(label)
                allLabels.add(labelName)
                labelDisplayToValue.putIfAbsent(labelName, label)
            }
        }

        return allLabels.sorted()
    }
    
    private fun applyLabelFilter(list: List<RealmNews?>): List<RealmNews?> {
        if (selectedLabel == "All") {
            return list
        }
        
        return list.filter { news ->
            when {
                selectedLabel == "Shared Chat" -> {
                    news?.chat == true || news?.viewableBy.equals("community", ignoreCase = true)
                }
                labelDisplayToValue.containsKey(selectedLabel) -> {
                    val labelValue = labelDisplayToValue[selectedLabel]
                    news?.labels?.contains(labelValue) == true
                }
                else -> {
                    extractSharedTeamName(news) == selectedLabel
                }
            }
        }
    }
    
    private fun extractSharedTeamName(news: RealmNews?): String {
        if (!news?.viewIn.isNullOrEmpty()) {
            try {
                val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
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

    override fun onDestroyView() {
        updatedNewsList?.removeAllChangeListeners()
        updatedNewsList = null
        if (isRealmInitialized()) {
            mRealm.close()
        }
        _binding = null
        super.onDestroyView()
    }
}
