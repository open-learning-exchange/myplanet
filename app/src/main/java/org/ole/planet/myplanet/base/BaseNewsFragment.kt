package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import java.io.File
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ImageThumbBinding
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.dto.NewsItem
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.news.AdapterNews.OnNewsItemClickListener
import org.ole.planet.myplanet.ui.news.NewsActions
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.GsonUtils
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(api = Build.VERSION_CODES.O)
abstract class BaseNewsFragment : BaseContainerFragment(), OnNewsItemClickListener {
    lateinit var imageList: RealmList<String>
    @JvmField
    protected var llImage: ViewGroup? = null
    @JvmField
    protected var adapterNews: AdapterNews? = null
    lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    private lateinit var replyActivityLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageList = RealmList()
        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        processImageUri(uri, result.resultCode)
                    }
                } else {
                    val uri = data?.data
                    processImageUri(uri, result.resultCode)
                }
            }
        }
        replyActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val newsId = result.data?.getStringExtra("newsId")
                newsId.let { adapterNews?.updateReplyBadge(it) }
                adapterNews?.refreshCurrentItems()
            }
        }
    }

    override fun onDataChanged() {
        adapterNews?.refreshCurrentItems()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) homeItemClickListener = context
    }

    override fun showReply(news: NewsItem, fromLogin: Boolean, nonTeamMember: Boolean) {
        prefData.setRepliedNewsId(news.id)
        val intent = Intent(activity, ReplyActivity::class.java).putExtra("id", news.id)
            .putExtra("fromLogin", fromLogin)
            .putExtra("nonTeamMember", nonTeamMember)
        replyActivityLauncher.launch(intent)
    }

    override fun onMemberSelected(news: NewsItem) {
        if (!isAdded) return
        val userModel = mRealm.where(RealmUserModel::class.java).equalTo("id", news.userId).findFirst()
        val handler = profileDbHandler
        val fragment = NewsActions.showMemberDetails(userModel, handler) ?: return
        NavigationHelper.replaceFragment(
            requireActivity().supportFragmentManager,
            R.id.fragment_container,
            fragment,
            addToBackStack = true
        )
    }

    abstract fun setData(list: List<RealmNews?>?)
    fun showNoData(v: View?, count: Int?, source: String) {
        count?.let { BaseRecyclerFragment.showNoData(v, it, source) }
    }


    fun changeLayoutManager(orientation: Int, recyclerView: RecyclerView) {
        activity?.let { act ->
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                recyclerView.layoutManager = GridLayoutManager(act, 2)
            } else {
                recyclerView.layoutManager = LinearLayoutManager(act)
            }
        }
    }

    override fun addImage(llImage: ViewGroup?) {
        this.llImage = llImage
        val openFolderIntent = FileUtils.openOleFolder(requireContext())
        openFolderLauncher.launch(openFolderIntent)
    }

    override fun getCurrentImageList(): List<String>? {
        return if (::imageList.isInitialized) imageList else null
    }

    private fun processImageUri(uri: Uri?, resultCode: Int) {
        if (uri == null) return

        var path: String? = getRealPathFromURI(requireActivity(), uri)
        if (TextUtils.isEmpty(path)) {
            path = FileUtils.getPathFromURI(requireActivity(), uri)
        }

        if (path.isNullOrEmpty()) return

        val `object` = JsonObject()
        `object`.addProperty("imageUrl", path)
        `object`.addProperty("fileName", getFileNameFromUrl(path))
        imageList.add(GsonUtils.gson.toJson(`object`))

        try {
            llImage?.visibility = View.VISIBLE
            val imageBinding = ImageThumbBinding.inflate(LayoutInflater.from(activity), llImage, false)
            Glide.with(requireActivity())
                .load(File(path))
                .into(imageBinding.thumb)
            llImage?.addView(imageBinding.root)
            if (resultCode == 102) adapterNews?.setImageList(imageList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onEditNews(news: NewsItem) {
        val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst() ?: return
        NewsActions.showEditAlert(requireContext(), mRealm, news.id, true, profileDbHandler.userModel, this, null)
    }

    override fun onDeleteNews(news: NewsItem) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setMessage(R.string.delete_record)
            .setPositiveButton(R.string.ok) { _, _ ->
                 val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
                 NewsActions.deletePost(mRealm, realmNews, mutableListOf(), getTeamName(), this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onReplyNews(news: NewsItem) {
        NewsActions.showEditAlert(requireContext(), mRealm, news.id, false, profileDbHandler.userModel, this, null)
    }

    override fun onShareNews(news: NewsItem) {
         val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst() ?: return
         AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle(R.string.share_with_community)
                .setMessage(R.string.confirm_share_community)
                .setPositiveButton(R.string.yes) { _, _ ->
                    val array = GsonUtils.gson.fromJson(realmNews.viewIn, JsonArray::class.java)
                    val ob = JsonObject()
                    ob.addProperty("section", "community")
                    ob.addProperty("_id", profileDbHandler.userModel?.planetCode + "@" + profileDbHandler.userModel?.parentCode)
                    ob.addProperty("sharedDate", java.util.Calendar.getInstance().timeInMillis)

                    if (array.size() > 0) {
                        val firstElement = array.get(0).asJsonObject
                         if(!firstElement.has("name")){
                            firstElement.addProperty("name", getTeamName())
                        }
                    }

                    array.add(ob)
                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    realmNews.sharedBy = profileDbHandler.userModel?.id
                    realmNews.viewIn = GsonUtils.gson.toJson(array)
                    mRealm.commitTransaction()
                    org.ole.planet.myplanet.utilities.Utilities.toast(requireContext(), getString(R.string.shared_to_community))
                    onDataChanged()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun onAddLabel(news: NewsItem, view: View) {
         val usedLabels = news.labels?.toSet() ?: emptySet()
         val availableLabels = Constants.LABELS.filterValues { it !in usedLabels }

         val wrapper = androidx.appcompat.view.ContextThemeWrapper(requireContext(), R.style.CustomPopupMenu)
         val menu = android.widget.PopupMenu(wrapper, view)
         availableLabels.keys.forEach { labelName ->
             menu.menu.add(labelName)
         }
         menu.setOnMenuItemClickListener { menuItem ->
             val selectedLabel = Constants.LABELS[menuItem.title]
             val newsId = news.id
             if (selectedLabel != null && newsId != null) {
                 if (news.labels?.contains(selectedLabel) == true) {
                     return@setOnMenuItemClickListener true
                 }
                 val labelAdded = AtomicBoolean(false)
                 mRealm.executeTransactionAsync(Realm.Transaction { transactionRealm ->
                     val managedNews = transactionRealm.where(RealmNews::class.java)
                         .equalTo("id", newsId)
                         .findFirst()
                     if (managedNews != null) {
                         var managedLabels = managedNews.labels
                         if (managedLabels == null) {
                             managedLabels = RealmList()
                             managedNews.labels = managedLabels
                         }
                         if (!managedLabels.contains(selectedLabel)) {
                             managedLabels.add(selectedLabel)
                             labelAdded.set(true)
                         }
                     }
                 }, Realm.Transaction.OnSuccess {
                     if (labelAdded.get()) {
                         org.ole.planet.myplanet.utilities.Utilities.toast(requireContext(), getString(R.string.label_added))
                         onDataChanged()
                     }
                 })
                 return@setOnMenuItemClickListener false
             }
             true
         }
         menu.show()
    }

    override fun onRemoveLabel(news: NewsItem, label: String) {
        val newsId = news.id ?: return
         val labelRemoved = AtomicBoolean(false)
         mRealm.executeTransactionAsync(Realm.Transaction { transactionRealm ->
             val managedNews = transactionRealm.where(RealmNews::class.java)
                 .equalTo("id", newsId)
                 .findFirst()
             managedNews?.labels?.let { managedLabels ->
                 if (managedLabels.remove(label)) {
                     labelRemoved.set(true)
                 }
             }
         }, Realm.Transaction.OnSuccess {
             if (labelRemoved.get()) {
                 onDataChanged()
             }
         })
    }

    override fun onNewsItemClick(news: NewsItem) {
    }

    open fun getTeamName(): String = ""
}
