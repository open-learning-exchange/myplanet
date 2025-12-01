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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.RealmList
import java.io.File
import java.util.Calendar
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.ImageThumbBinding
import org.ole.planet.myplanet.model.NewsItem
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.news.AdapterNews.OnNewsItemClickListener
import org.ole.planet.myplanet.ui.news.NewsActions
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.Utilities

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

    override fun showReply(news: NewsItem?, fromLogin: Boolean, nonTeamMember: Boolean) {
        if (news != null) {
            val intent = Intent(activity, ReplyActivity::class.java).putExtra("id", news.id)
                .putExtra("fromLogin", fromLogin)
                .putExtra("nonTeamMember", nonTeamMember)
            replyActivityLauncher.launch(intent)
        }
    }

    override fun onMemberSelected(news: NewsItem) {
        if (!isAdded) return
        val userId = news.userId
        if (userId != null) {
            val userModel = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            val handler = profileDbHandler
            val fragment = NewsActions.showMemberDetails(userModel, handler) ?: return
            NavigationHelper.replaceFragment(
                requireActivity().supportFragmentManager,
                R.id.fragment_container,
                fragment,
                addToBackStack = true
            )
        }
    }

    abstract fun setData(list: List<NewsItem>?)
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

    override fun getCurrentImageList(): RealmList<String>? {
        return if (::imageList.isInitialized) imageList else null
    }

    override fun onDelete(news: NewsItem) {
        val realmNews = mRealm.where(RealmNews::class.java).equalTo("id", news.id).findFirst()
        if(realmNews != null) {
             NewsActions.deletePost(mRealm, realmNews, mutableListOf(), getTeamName(), this)
             // Note: empty teamName might be wrong for DiscussionListFragment.
             // But DiscussionListFragment can override getTeamName.
        }
    }

    override fun onEdit(news: NewsItem, holder: RecyclerView.ViewHolder) {
         val user = profileDbHandler.userModel
         NewsActions.showEditAlert(requireContext(), mRealm, news.id, true, user, this, holder) { _, _, _ ->
             adapterNews?.updateReplyBadge(news.id)
         }
    }

    override fun onReply(news: NewsItem, holder: RecyclerView.ViewHolder) {
         val user = profileDbHandler.userModel
         NewsActions.showEditAlert(requireContext(), mRealm, news.id, false, user, this, holder) { _, _, _ ->
             adapterNews?.updateReplyBadge(news.id)
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
                        obj.addProperty("name", getTeamName())
                    }
                    val ob = JsonObject()
                    ob.addProperty("section", "community")
                    val user = profileDbHandler.userModel
                    ob.addProperty("_id", user?.planetCode + "@" + user?.parentCode)
                    ob.addProperty("sharedDate", Calendar.getInstance().timeInMillis)
                    array.add(ob)

                    if (!mRealm.isInTransaction) {
                        mRealm.beginTransaction()
                    }
                    realmNews.sharedBy = user?.id
                    realmNews.viewIn = GsonUtils.gson.toJson(array)
                    mRealm.commitTransaction()
                    Utilities.toast(context, getString(R.string.shared_to_community))
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
                     managedLabels = RealmList()
                     managedNews.labels = managedLabels
                 }
                 if (!managedLabels.contains(label)) {
                     managedLabels.add(label)
                 }
             }
        }, {
             Utilities.toast(context, getString(R.string.label_added))
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

    open fun getTeamName(): String = ""

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
            if (resultCode == 102) adapterNews?.addItem(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
