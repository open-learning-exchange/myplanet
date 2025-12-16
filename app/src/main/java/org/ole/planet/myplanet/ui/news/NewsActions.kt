package org.ole.planet.myplanet.ui.news

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import io.realm.RealmList
import java.io.File
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNews.Companion.createNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.teamMember.MemberDetailFragment
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.NewsRepository

object NewsActions {
    data class EditDialogComponents(
        val view: View,
        val editText: EditText,
        val inputLayout: TextInputLayout,
        val imageLayout: ViewGroup
    )

    fun createEditDialogComponents(
        context: Context,
        listener: AdapterNews.OnNewsItemClickListener?
    ): EditDialogComponents {
        val v = android.view.LayoutInflater.from(context).inflate(R.layout.alert_input, null)
        val tlInput = v.findViewById<TextInputLayout>(R.id.tl_input)
        val et = v.findViewById<EditText>(R.id.et_input)
        val llImage = v.findViewById<ViewGroup>(R.id.ll_alert_image)
        v.findViewById<View>(R.id.add_news_image).setOnClickListener { listener?.addImage(llImage) }
        return EditDialogComponents(v, et, tlInput, llImage)
    }

    private fun loadExistingImages(context: Context, news: RealmNews?, imageLayout: ViewGroup, imagesToRemove: MutableSet<String>) {
        imagesToRemove.clear()
        imageLayout.removeAllViews()

        val imageUrls = news?.imageUrls
        if (!imageUrls.isNullOrEmpty()) {
            imageUrls.forEach { imageUrl ->
                try {
                    val imgObject = GsonUtils.gson.fromJson(imageUrl, JsonObject::class.java)
                    val path = JsonUtils.getString("imageUrl", imgObject)
                    if (path.isNotEmpty()) {
                        addImageWithRemoveIcon(context, path, imageLayout, imagesToRemove)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun addImageWithRemoveIcon(context: Context, imagePath: String, imageLayout: ViewGroup, imagesToRemove: MutableSet<String>) {
        val frameLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                dpToPx(context, 100),
                dpToPx(context, 100)
            ).apply {
                setMargins(dpToPx(context, 4), dpToPx(context, 4), dpToPx(context, 4), dpToPx(context, 4))
            }
        }

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val request = Glide.with(context)
        val target = if (imagePath.lowercase(Locale.getDefault()).endsWith(".gif")) {
            request.asGif().load(if (File(imagePath).exists()) File(imagePath) else imagePath)
        } else {
            request.load(if (File(imagePath).exists()) File(imagePath) else imagePath)
        }
        target.placeholder(R.drawable.ic_loading)
            .error(R.drawable.ic_loading)
            .into(imageView)

        val removeIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(context, 24),
                dpToPx(context, 24)
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(dpToPx(context, 4), dpToPx(context, 4), dpToPx(context, 4), dpToPx(context, 4))
            }
            setImageResource(R.drawable.baseline_close_24)
            background = ContextCompat.getDrawable(context, R.drawable.rounded_background)
            setPadding(dpToPx(context, 4), dpToPx(context, 4), dpToPx(context, 4), dpToPx(context, 4))
            setOnClickListener {
                imagesToRemove.add(imagePath)
                (parent as? ViewGroup)?.let { parentView ->
                    (parentView.parent as? ViewGroup)?.removeView(parentView)
                }
            }
        }

        frameLayout.addView(imageView)
        frameLayout.addView(removeIcon)
        imageLayout.addView(frameLayout)
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun handlePositiveButton(
        dialog: AlertDialog,
        isEdit: Boolean,
        components: EditDialogComponents,
        news: RealmNews?,
        newsRepository: NewsRepository,
        currentUser: RealmUserModel?,
        imageList: RealmList<String>?,
        listener: AdapterNews.OnNewsItemClickListener?,
        imagesToRemove: MutableSet<String>
    ) {
        val s = components.editText.text.toString().trim()
        if (s.isEmpty()) {
            components.inputLayout.error = dialog.context.getString(R.string.please_enter_message)
            return
        }
        if (isEdit) {
            editPost(newsRepository, s, news, imageList, imagesToRemove)
        } else {
            postReply(newsRepository, s, news, currentUser, imageList)
        }
        dialog.dismiss()
        listener?.clearImages()
        listener?.onDataChanged()
    }

    fun showEditAlert(
        context: Context,
        newsRepository: NewsRepository,
        news: RealmNews?,
        isEdit: Boolean,
        currentUser: RealmUserModel?,
        listener: AdapterNews.OnNewsItemClickListener?,
        viewHolder: RecyclerView.ViewHolder,
        updateReplyButton: (RecyclerView.ViewHolder, RealmNews?, Int) -> Unit = { _, _, _ -> }
    ) {
        val imagesToRemove = mutableSetOf<String>()
        val components = createEditDialogComponents(context, listener)
        val message = components.view.findViewById<TextView>(R.id.cust_msg)
        message.text = context.getString(if (isEdit) R.string.edit_post else R.string.reply)
        val icon = components.view.findViewById<ImageView>(R.id.alert_icon)
        icon.setImageResource(R.drawable.ic_edit)
        if (isEdit) {
            components.editText.setText(context.getString(R.string.message_placeholder, news?.message))
            loadExistingImages(context, news, components.imageLayout, imagesToRemove)
        }
        val dialog = AlertDialog.Builder(context, R.style.ReplyAlertDialog)
            .setView(components.view)
            .setPositiveButton(R.string.button_submit, null)
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val currentImageList = listener?.getCurrentImageList()
            handlePositiveButton(dialog, isEdit, components, news, newsRepository, currentUser, currentImageList, listener, imagesToRemove)
            updateReplyButton(viewHolder, news, viewHolder.bindingAdapterPosition)
        }
    }

    private fun postReply(
        newsRepository: NewsRepository,
        s: String?,
        news: RealmNews?,
        currentUser: RealmUserModel?,
        imageList: RealmList<String>?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            newsRepository.postReply(s ?: "", news?.id ?: "", currentUser, imageList)
        }
    }

    private fun editPost(
        newsRepository: NewsRepository,
        s: String,
        news: RealmNews?,
        imageList: RealmList<String>?,
        imagesToRemove: MutableSet<String>
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            news?.id?.let {
                newsRepository.editPost(
                    it,
                    s,
                    imagesToRemove,
                    imageList ?: emptyList()
                )
            }
            imagesToRemove.clear()
        }
    }

    fun showMemberDetails(
        userModel: RealmUserModel?,
        profileDbHandler: UserProfileDbHandler
    ): MemberDetailFragment? {
        if (userModel == null) return null
        val userName = "${userModel.firstName} ${userModel.lastName}".trim().ifBlank { userModel.name }
        val fragment = MemberDetailFragment.newInstance(
            userName.toString(),
            userModel.email.toString(),
            userModel.dob.toString().substringBefore("T"),
            userModel.language.toString(),
            userModel.phoneNumber.toString(),
            profileDbHandler.getOfflineVisits(userModel).toString(),
            profileDbHandler.getLastVisit(userModel),
            "${userModel.firstName} ${userModel.lastName}",
            userModel.level.toString(),
            userModel.userImage
        )
        return fragment
    }

    fun deletePost(
        newsRepository: NewsRepository,
        news: RealmNews?,
        teamName: String,
        onDeleted: () -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            news?.id?.let { newsRepository.deletePost(it, teamName) }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
    }

}
