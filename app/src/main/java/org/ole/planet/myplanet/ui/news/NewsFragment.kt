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
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.databinding.FragmentNewsBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.KeyboardUtils

@AndroidEntryPoint
class NewsFragment : BaseNewsFragment() {
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!
    var user: RealmUserModel? = null

    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    @Inject
    lateinit var newsRepository: NewsRepository

    private var newsList: List<RealmNews?> = listOf()
    private var filteredNewsList: List<RealmNews?> = listOf()
    private var searchFilteredList: List<RealmNews?> = listOf()
    private var labelFilteredList: List<RealmNews?> = listOf()
    private lateinit var etSearch: EditText
    private var selectedLabel: String = "All"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        llImage = binding.llImages
        user = UserProfileDbHandler(requireContext()).userModel
        KeyboardUtils.setupUI(binding.newsFragmentParentLayout, requireActivity())
        if (user?.id?.startsWith("guest") == true) {
            binding.btnNewVoice.visibility = View.GONE
        }
        binding.btnNewVoice.setOnClickListener {
            binding.llAddNews.visibility = if (binding.llAddNews.isVisible) View.GONE else View.VISIBLE
            binding.btnNewVoice.text = if (binding.llAddNews.isVisible) getString(R.string.hide_new_voice) else getString(R.string.new_voice)
        }
        if (requireArguments().getBoolean("fromLogin")) {
            binding.btnNewVoice.visibility = View.GONE
            binding.llAddNews.visibility = View.GONE
        }

        etSearch = binding.root.findViewById(R.id.et_search)
        setupSearchTextListener()
        setupLabelFilter()
        loadNews()

        return binding.root
    }

    private fun loadNews() {
        lifecycleScope.launch {
            newsList = newsRepository.getNews(user?.id, settings.getString("planetCode", ""), settings.getString("parentCode", ""))
            filteredNewsList = newsList
            updateLabelSpinner()
            filterAndDisplayNews()
        }
    }

    private fun filterAndDisplayNews() {
        labelFilteredList = applyLabelFilter(filteredNewsList)
        searchFilteredList = applySearchFilter(labelFilteredList)
        setData(searchFilteredList)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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

            lifecycleScope.launch {
                val n = user?.let { it1 -> newsRepository.createNews(map, it1, imageList) }
                imageList.clear()
                llImage?.removeAllViews()
                adapterNews?.addItem(n)
                loadNews() // Reload all news to reflect the new addition
            }
        }

        binding.addNewsImage.setOnClickListener {
            llImage = binding.llImages
            val openFolderIntent = FileUtils.openOleFolder()
            openFolderLauncher.launch(openFolderIntent)
        }
        binding.addNewsImage.visibility = if (Constants.showBetaFeature(Constants.KEY_NEWSADDIMAGE, requireActivity())) View.VISIBLE else View.GONE
    }

    override fun setData(list: List<RealmNews?>?) {
        if (!isAdded || list == null) return

        if (binding.rvNews.adapter == null) {
            changeLayoutManager(resources.configuration.orientation, binding.rvNews)
            val updatedListAsMutable: MutableList<RealmNews?> = list.toMutableList()
            val sortedList = updatedListAsMutable.sortedWith(compareByDescending { news ->
                getSortDate(news)
            })
            adapterNews = AdapterNews(requireActivity(), sortedList.toMutableList(), user, null, "", null, userProfileDbHandler, newsRepository, viewLifecycleOwner)
            adapterNews?.setFromLogin(requireArguments().getBoolean("fromLogin"))
            adapterNews?.setListener(this)
            adapterNews?.registerAdapterDataObserver(observer)
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
            NavigationHelper.replaceFragment(parentFragmentManager, R.id.fragment_container, chatDetailFragment, true)
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

    private val observer: RecyclerView.AdapterDataObserver = object : RecyclerView.AdapterDataObserver() {
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
                val ar = com.google.gson.Gson().fromJson(news.viewIn, com.google.gson.JsonArray::class.java)
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
                filterAndDisplayNews()
            }
            override fun afterTextChanged(s: Editable) {}
        })
    }

    private fun applySearchFilter(list: List<RealmNews?>): List<RealmNews?> {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) return list
        return list.filter { news ->
            val message = news?.message?.trim() ?: ""
            message.contains(query, ignoreCase = true)
        }
    }

    private fun setupLabelFilter() {
        updateLabelSpinner()
        binding.filterByLabel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedLabel = (binding.filterByLabel.adapter as ArrayAdapter<String>).getItem(position) ?: "All"
                filterAndDisplayNews()
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
        val allLabels = mutableSetOf("All")
        Constants.LABELS.keys.forEach { allLabels.add(it) }
        allLabels.add("Shared Chat")
        list.forEach { news ->
            if (!news?.viewIn.isNullOrEmpty()) {
                try {
                    val ar = com.google.gson.Gson().fromJson(news.viewIn, com.google.gson.JsonArray::class.java)
                    if (ar.size() > 1) {
                        val ob = ar[0].asJsonObject
                        if (ob.has("name") && !ob.get("name").isJsonNull) {
                            ob.get("name").asString.takeIf { it.isNotEmpty() }?.let { allLabels.add(it) }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            news?.labels?.forEach { label ->
                Constants.LABELS.entries.find { it.value == label }?.key?.let { allLabels.add(it) }
            }
        }
        return allLabels.sorted()
    }

    private fun applyLabelFilter(list: List<RealmNews?>): List<RealmNews?> {
        if (selectedLabel == "All") return list
        return list.filter { news ->
            when {
                selectedLabel == "Shared Chat" -> news?.chat == true || news?.viewableBy.equals("community", ignoreCase = true)
                Constants.LABELS.containsKey(selectedLabel) -> news?.labels?.contains(Constants.LABELS[selectedLabel]) == true
                else -> extractSharedTeamName(news) == selectedLabel
            }
        }
    }

    private fun extractSharedTeamName(news: RealmNews?): String {
        if (!news?.viewIn.isNullOrEmpty()) {
            try {
                val ar = com.google.gson.Gson().fromJson(news.viewIn, com.google.gson.JsonArray::class.java)
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
        _binding = null
        super.onDestroyView()
    }
}
