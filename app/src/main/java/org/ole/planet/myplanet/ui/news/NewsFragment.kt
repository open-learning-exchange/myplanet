package org.ole.planet.myplanet.ui.news

import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.gson.Gson
import com.google.gson.JsonArray
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Case
import io.realm.RealmResults
import io.realm.Sort
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.databinding.FragmentNewsBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.FileUtils.openOleFolder
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI

@AndroidEntryPoint
class NewsFragment : BaseNewsFragment() {
    private lateinit var fragmentNewsBinding: FragmentNewsBinding
    var user: RealmUserModel? = null
    
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private var updatedNewsList: RealmResults<RealmNews>? = null
    private var filteredNewsList: List<RealmNews?> = listOf()
    private var searchFilteredList: List<RealmNews?> = listOf()
    private var labelFilteredList: List<RealmNews?> = listOf()
    private val gson = Gson()
    private lateinit var etSearch: EditText
    private var selectedLabel: String = "All"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentNewsBinding = FragmentNewsBinding.inflate(inflater, container, false)
        llImage = fragmentNewsBinding.llImages
        user = UserProfileDbHandler(requireContext()).userModel
        setupUI(fragmentNewsBinding.newsFragmentParentLayout, requireActivity())
        if (user?.id?.startsWith("guest") == true) {
            fragmentNewsBinding.btnNewVoice.visibility = View.GONE
        }
        fragmentNewsBinding.btnNewVoice.setOnClickListener {
            fragmentNewsBinding.llAddNews.visibility = if (fragmentNewsBinding.llAddNews.isVisible) {
                View.GONE
            } else {
                View.VISIBLE
            }
            fragmentNewsBinding.btnNewVoice.text = if (fragmentNewsBinding.llAddNews.isVisible) {
                getString(R.string.hide_new_voice)
            } else {
                getString(R.string.new_voice)
            }
        }
        if (requireArguments().getBoolean("fromLogin")) {
            fragmentNewsBinding.btnNewVoice.visibility = View.GONE
            fragmentNewsBinding.llAddNews.visibility = View.GONE
        }

        updatedNewsList = mRealm.where(RealmNews::class.java).sort("time", Sort.DESCENDING)
            .isEmpty("replyTo").equalTo("docType", "message", Case.INSENSITIVE)
            .findAllAsync()

        updatedNewsList?.addChangeListener { results ->
            filteredNewsList = filterNewsList(results)
            updateLabelSpinner()
            labelFilteredList = applyLabelFilter(filteredNewsList)
            searchFilteredList = applySearchFilter(labelFilteredList)
            setData(searchFilteredList)
        }
        
        etSearch = fragmentNewsBinding.root.findViewById(R.id.et_search)
        setupSearchTextListener()
        setupLabelFilter()
        
        return fragmentNewsBinding.root
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
        filteredNewsList = newsList
        setupLabelFilter()
        labelFilteredList = applyLabelFilter(filteredNewsList)
        searchFilteredList = applySearchFilter(labelFilteredList)
        setData(searchFilteredList)
        fragmentNewsBinding.btnSubmit.setOnClickListener {
            val message = fragmentNewsBinding.etMessage.text.toString().trim { it <= ' ' }
            if (message.isEmpty()) {
                fragmentNewsBinding.tlMessage.error = getString(R.string.please_enter_message)
                return@setOnClickListener
            }
            fragmentNewsBinding.etMessage.setText(R.string.empty_text)
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
        }

        fragmentNewsBinding.addNewsImage.setOnClickListener {
            llImage = fragmentNewsBinding.llImages
            val openFolderIntent = openOleFolder()
            openFolderLauncher.launch(openFolderIntent)
        }
        fragmentNewsBinding.addNewsImage.visibility = if (showBetaFeature(Constants.KEY_NEWSADDIMAGE, requireActivity())) View.VISIBLE else View.GONE
    }

    private val newsList: List<RealmNews?> get() {
        val allNews: List<RealmNews> = mRealm.where(RealmNews::class.java).isEmpty("replyTo")
            .equalTo("docType", "message", Case.INSENSITIVE).findAll()
        val list: MutableList<RealmNews?> = ArrayList()
        for (news in allNews) {
            if (!TextUtils.isEmpty(news.viewableBy) && news.viewableBy.equals("community", ignoreCase = true)) {
                list.add(news)
                continue
            }
            if (!TextUtils.isEmpty(news.viewIn)) {
                val ar = gson.fromJson(news.viewIn, JsonArray::class.java)
                for (e in ar) {
                    val ob = e.asJsonObject
                    var userId = "${user?.planetCode}@${user?.parentCode}"
                    if(userId.isEmpty() || userId=="@"){
                        userId = settings?.getString("planetCode","")+"@"+settings?.getString("parentCode", "")
                    }
                    if (ob != null && ob.has("_id") && ob["_id"].asString.equals(userId, ignoreCase = true)) {
                        list.add(news)
                    }
                }
            }
        }
        return list
    }

    override fun setData(list: List<RealmNews?>?) {
        if (!isAdded || list == null) return

        if (fragmentNewsBinding.rvNews.adapter == null) {
            changeLayoutManager(resources.configuration.orientation, fragmentNewsBinding.rvNews)
            val resourceIds: MutableList<String> = ArrayList()
            list.forEach { news ->
                if ((news?.imagesArray?.size() ?: 0) > 0) {
                    val ob = news?.imagesArray?.get(0)?.asJsonObject
                    val resourceId = getString("resourceId", ob?.asJsonObject)
                    resourceId.let {
                        resourceIds.add(it)
                    }
                }
            }
            val lib: List<RealmMyLibrary?> = mRealm.where(RealmMyLibrary::class.java)
                .`in`("_id", resourceIds.toTypedArray())
                .findAll()
            getUrlsAndStartDownload(lib, ArrayList())
            val updatedListAsMutable: MutableList<RealmNews?> = list.toMutableList()
            val sortedList = updatedListAsMutable.sortedWith(compareByDescending { news ->
                getSortDate(news)
            })
            adapterNews = AdapterNews(requireActivity(), sortedList.toMutableList(), user, null, "", null, userProfileDbHandler)

            adapterNews?.setmRealm(mRealm)
            adapterNews?.setFromLogin(requireArguments().getBoolean("fromLogin"))
            adapterNews?.setListener(this)
            adapterNews?.registerAdapterDataObserver(observer)

            fragmentNewsBinding.rvNews.adapter = adapterNews
        } else {
            (fragmentNewsBinding.rvNews.adapter as? AdapterNews)?.updateList(list)
        }
        adapterNews?.let { showNoData(fragmentNewsBinding.tvMessage, it.itemCount, "news") }
        fragmentNewsBinding.llAddNews.visibility = View.GONE
        fragmentNewsBinding.btnNewVoice.text = getString(R.string.new_voice)
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
        changeLayoutManager(orientation, fragmentNewsBinding.rvNews)
    }

    private val observer: AdapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            adapterNews?.let { showNoData(fragmentNewsBinding.tvMessage, it.itemCount, "news") }
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            adapterNews?.let { showNoData(fragmentNewsBinding.tvMessage, it.itemCount, "news") }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            adapterNews?.let { showNoData(fragmentNewsBinding.tvMessage, it.itemCount, "news") }
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

        fragmentNewsBinding.filterByLabel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val labels = (fragmentNewsBinding.filterByLabel.adapter as ArrayAdapter<String>)
                selectedLabel = labels.getItem(position) ?: "All"
                labelFilteredList = applyLabelFilter(filteredNewsList)
                searchFilteredList = applySearchFilter(labelFilteredList)
                setData(searchFilteredList)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun updateLabelSpinner() {
        val labels = collectAllLabels(filteredNewsList)
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(requireContext(), R.style.ResourcePopupMenu)
        val adapter = ArrayAdapter(themedContext, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fragmentNewsBinding.filterByLabel.adapter = adapter

        val position = labels.indexOf(selectedLabel)
        if (position >= 0) {
            fragmentNewsBinding.filterByLabel.setSelection(position)
        } else {
            selectedLabel = "All"
            fragmentNewsBinding.filterByLabel.setSelection(0)
        }
    }
    
    private fun collectAllLabels(list: List<RealmNews?>): List<String> {
        val allLabels = mutableSetOf<String>()
        allLabels.add("All")

        Constants.LABELS.keys.forEach { labelName ->
            allLabels.add(labelName)
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
                Constants.LABELS.entries.find { it.value == label }?.key?.let { labelName ->
                    allLabels.add(labelName)
                }
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
                Constants.LABELS.containsKey(selectedLabel) -> {
                    val labelValue = Constants.LABELS[selectedLabel]
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
}
