package org.ole.planet.myplanet.ui.news

import android.content.res.Configuration
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.gson.Gson
import com.google.gson.JsonArray
import io.realm.Case
import io.realm.RealmResults
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseNewsFragment
import org.ole.planet.myplanet.databinding.FragmentNewsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserChallengeActions.Companion.createAction
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.chat.ChatDetailFragment
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.showBetaFeature
import org.ole.planet.myplanet.utilities.FileUtils.openOleFolder
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI

class NewsFragment : BaseNewsFragment() {
    private lateinit var fragmentNewsBinding: FragmentNewsBinding
    var user: RealmUserModel? = null
    private var updatedNewsList: RealmResults<RealmNews>? = null
    private var filteredNewsList: List<RealmNews?> = listOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentNewsBinding = FragmentNewsBinding.inflate(inflater, container, false)
        llImage = fragmentNewsBinding.llImages
        mRealm = DatabaseService(requireActivity()).realmInstance
        user = UserProfileDbHandler(requireContext()).userModel
        setupUI(fragmentNewsBinding.newsFragmentParentLayout, requireActivity())
        if (user?.id?.startsWith("guest") == true) {
            fragmentNewsBinding.btnNewVoice.visibility = View.GONE
        }
        fragmentNewsBinding.btnNewVoice.setOnClickListener {
            fragmentNewsBinding.llAddNews.visibility = if (fragmentNewsBinding.llAddNews.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
            fragmentNewsBinding.btnNewVoice.text = if (fragmentNewsBinding.llAddNews.visibility == View.VISIBLE) {
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
            setData(filteredNewsList)
        }
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
                val ar = Gson().fromJson(news.viewIn, JsonArray::class.java)
                for (e in ar) {
                    val ob = e.asJsonObject
                    if (ob != null && ob.has("_id") && ob["_id"].asString.equals("${user?.planetCode}@${user?.parentCode}", ignoreCase = true)) {
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
        setData(newsList)
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
            setData(newsList)

            createAction(mRealm, "${user?.id}", "${n?.id}", "voice")
        }

        fragmentNewsBinding.addNewsImage.setOnClickListener {
            llImage = fragmentNewsBinding.llImages
            val openFolderIntent = openOleFolder()
            openFolderLauncher.launch(openFolderIntent)
        }
        fragmentNewsBinding.addNewsImage.visibility = if (showBetaFeature(Constants.KEY_NEWSADDIMAGE, requireActivity())) View.VISIBLE else View.GONE
    }

    private val newsList: List<RealmNews?> get() {
        val allNews: List<RealmNews> = mRealm.where(RealmNews::class.java).sort("time", Sort.DESCENDING).isEmpty("replyTo")
            .equalTo("docType", "message", Case.INSENSITIVE).findAll()
        val list: MutableList<RealmNews?> = ArrayList()
        for (news in allNews) {
            if (!TextUtils.isEmpty(news.viewableBy) && news.viewableBy.equals("community", ignoreCase = true)) {
                list.add(news)
                continue
            }
            if (!TextUtils.isEmpty(news.viewIn)) {
                val ar = Gson().fromJson(news.viewIn, JsonArray::class.java)
                for (e in ar) {
                    val ob = e.asJsonObject
                    if (ob != null && ob.has("_id") && ob["_id"].asString.equals(if (user != null) user?.planetCode + "@" + user?.parentCode else "", ignoreCase = true)) {
                        list.add(news)
                    }
                }
            }
        }
        return list
    }

    override fun setData(list: List<RealmNews?>?) {
        if (isAdded) {
            changeLayoutManager(resources.configuration.orientation, fragmentNewsBinding.rvNews)
            val resourceIds: MutableList<String> = ArrayList()
            list?.forEach { news ->
                if ((news?.imagesArray?.size() ?: 0) > 0) {
                    val ob = news?.imagesArray?.get(0)?.asJsonObject
                    val resourceId = getString("resourceId", ob?.asJsonObject)
                    resourceId.let {
                        resourceIds.add(it)
                    }
                }
            }
            val urls = ArrayList<String>()
            val stringArray: Array<String?> = resourceIds.toTypedArray()
            val lib: List<RealmMyLibrary?> = mRealm.where(RealmMyLibrary::class.java)
                .`in`("_id", stringArray)
                .findAll()
            getUrlsAndStartDownload(lib, urls)
            adapterNews = activity?.let {
                AdapterNews(it, list?.toMutableList() ?: mutableListOf(), user, null)
            }
            adapterNews?.setmRealm(mRealm)
            adapterNews?.setFromLogin(requireArguments().getBoolean("fromLogin"))
            adapterNews?.setListener(this)
            adapterNews?.registerAdapterDataObserver(observer)
            fragmentNewsBinding.rvNews.adapter = adapterNews
            adapterNews?.let { showNoData(fragmentNewsBinding.tvMessage, it.itemCount, "news") }
            fragmentNewsBinding.llAddNews.visibility = View.GONE
            fragmentNewsBinding.btnNewVoice.text = getString(R.string.new_voice)
            adapterNews?.notifyDataSetChanged()
        }
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

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, chatDetailFragment)
                .addToBackStack(null)
                .commit()
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
}