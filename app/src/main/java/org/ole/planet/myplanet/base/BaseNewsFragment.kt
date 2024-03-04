package org.ole.planet.myplanet.base

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.realm.RealmList
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.news.AdapterNews
import org.ole.planet.myplanet.ui.news.AdapterNews.OnNewsItemClickListener
import org.ole.planet.myplanet.ui.news.ReplyActivity
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getRealPathFromURI
import org.ole.planet.myplanet.utilities.FileUtils.openOleFolder
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import java.io.File

@RequiresApi(api = Build.VERSION_CODES.O)
abstract class BaseNewsFragment : BaseContainerFragment(), OnNewsItemClickListener {
    lateinit var imageList: RealmList<String>
    @JvmField
    protected var llImage: LinearLayout? = null
    @JvmField
    protected var adapterNews: AdapterNews? = null
    lateinit var openFolderLauncher: ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageList = RealmList()
        profileDbHandler = UserProfileDbHandler(requireContext())
        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                var path: String
                val url: Uri? = data?.data
                path = getRealPathFromURI(requireActivity(), url)
                if (TextUtils.isEmpty(path)) {
                    path = getImagePath(url)
                }
                val `object` = JsonObject()
                `object`.addProperty("imageUrl", path)
                `object`.addProperty("fileName", getFileNameFromUrl(path))
                imageList.add(Gson().toJson(`object`))
                try {
                    llImage?.removeAllViews()
                    llImage?.visibility = View.VISIBLE
                    for (img in imageList) {
                        val ob = Gson().fromJson(img, JsonObject::class.java)
                            val inflater = LayoutInflater.from(activity).inflate(R.layout.image_thumb, null)
                            val imgView = inflater.findViewById<ImageView>(R.id.thumb)
                            Glide.with(requireActivity()).load(File(getString("imageUrl", ob))).into(imgView)
                            llImage?.addView(inflater)
                        }
                        if (result.resultCode == 102) adapterNews?.setImageList(imageList)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnHomeItemClickListener) homeItemClickListener = context
    }

    override fun onDestroy() {
        super.onDestroy()
        if (profileDbHandler != null) profileDbHandler.onDestory()
    }

    override fun showReply(news: RealmNews?, fromLogin: Boolean) {
        if (news != null) {
            startActivity(
                Intent(activity, ReplyActivity::class.java).putExtra("id", news.id)
                    .putExtra("fromLogin", fromLogin)
            )
        }
    }

    abstract fun setData(list: List<RealmNews?>?)
    fun showNoData(v: View?, count: Int?) {
        count?.let { BaseRecyclerFragment.showNoData(v, it) }
    }

    private fun getImagePath(uri: Uri?): String {
        var cursor = uri?.let { requireContext().contentResolver.query(it, null, null, null, null) }
        cursor?.moveToFirst()
        var document_id = cursor?.getString(0)
        document_id = document_id?.substring(document_id.lastIndexOf(":") + 1)
        cursor?.close()
        cursor = requireContext().contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Images.Media._ID + " = ? ", arrayOf(document_id), null)
        cursor?.moveToFirst()
        val path = cursor!!.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
        cursor.close()
        return path
    }

    fun changeLayoutManager(orientation: Int, recyclerView: RecyclerView) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            recyclerView.layoutManager = GridLayoutManager(activity, 2)
        } else {
            recyclerView.layoutManager = LinearLayoutManager(activity)
        }
    }

    override fun addImage(llImage: LinearLayout?) {
        this.llImage = llImage
        val openFolderIntent = openOleFolder()
        openFolderLauncher.launch(openFolderIntent)
    }
}