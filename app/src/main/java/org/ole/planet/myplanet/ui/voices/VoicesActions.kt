package org.ole.planet.myplanet.ui.voices

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnNewsItemClickListener
import org.ole.planet.myplanet.databinding.AlertInputBinding
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.VoicesEditActions
import org.ole.planet.myplanet.ui.teams.members.MembersDetailFragment
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.Utilities

object VoicesActions {
    private val dateFormatter = ThreadLocal.withInitial { SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()) }

    data class EditDialogComponents(
        val binding: AlertInputBinding,
        val view: View,
        val editText: EditText,
        val inputLayout: TextInputLayout,
        val imageLayout: ViewGroup
    )

    fun createEditDialogComponents(
        context: Context,
        listener: OnNewsItemClickListener?
    ): EditDialogComponents {
        val binding = AlertInputBinding.inflate(LayoutInflater.from(context))
        val v = binding.root
        val tlInput = binding.tlInput
        val et = binding.etInput
        val llImage = binding.llAlertImage
        binding.addNewsImage.setOnClickListener { listener?.addImage(llImage) }
        return EditDialogComponents(binding, v, et, tlInput, llImage)
    }

    private fun loadExistingImages(context: Context, news: News?, imageLayout: ViewGroup, imagesToRemove: MutableSet<String>) {
        imageLayout.removeAllViews()

        val imageUrls = news?.imageUrls
        if (!imageUrls.isNullOrEmpty()) {
            imageUrls.forEach { imageUrl ->
                try {
                    val imgObject = JsonUtils.gson.fromJson(imageUrl, JsonObject::class.java)
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

    suspend fun handlePositiveButton(
        dialog: AlertDialog,
        isEdit: Boolean,
        components: EditDialogComponents,
        news: News?,
        repository: VoicesEditActions,
        currentUser: UserEntity?,
        imageList: List<String>?,
        listener: OnNewsItemClickListener?,
        imagesToRemove: MutableSet<String>,
        onSuccess: (News?) -> Unit
    ) {
        val s = components.editText.text.toString().trim()
        if (s.isEmpty()) {
            components.inputLayout.error = dialog.context.getString(R.string.please_enter_message)
            return
        }
        val imagesToRemoveCopy = imagesToRemove.toSet()
        imagesToRemove.clear()
        dialog.dismiss()
        try {
            val updatedNews = if (isEdit) {
                news?.id?.let {
                    repository.editPost(it, s, imagesToRemoveCopy, imageList)
                }
            } else {
                if (news != null && currentUser != null) {
                    repository.postReply(s, news, currentUser, imageList)
                }
                null
            }
            listener?.clearImages()
            if (isEdit) listener?.onDataChanged() else listener?.onReplyPosted(news?.id)
            onSuccess(updatedNews)
        } catch (e: Exception) {
            Utilities.toast(dialog.context, "An error occurred: ${e.message}")
        }
    }

    suspend fun showEditAlert(
        context: Context,
        id: String?,
        isEdit: Boolean,
        currentUser: UserEntity?,
        listener: OnNewsItemClickListener?,
        viewHolder: RecyclerView.ViewHolder,
        repository: VoicesEditActions,
        updateReplyButton: (RecyclerView.ViewHolder, News?, Int) -> Unit = { _, _, _ -> },
        launchAction: (suspend () -> Unit) -> Unit
    ) {
        val components = createEditDialogComponents(context, listener)
        val message = components.binding.custMsg
        message.text = context.getString(if (isEdit) R.string.edit_post else R.string.reply)
        val icon = components.binding.alertIcon
        icon.setImageResource(R.drawable.ic_edit)
        val imagesToRemove = mutableSetOf<String>()

        val news = id?.let { repository.getNewsById(it) }

        if (isEdit) {
            listener?.clearImages()
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
            launchAction {
                handlePositiveButton(dialog, isEdit, components, news, repository, currentUser, currentImageList, listener, imagesToRemove) { updatedNews ->
                    updateReplyButton(viewHolder, updatedNews ?: news, viewHolder.bindingAdapterPosition)
                }
            }
        }
    }

    suspend fun showMemberDetails(
        userModel: UserEntity?,
        activitiesRepository: ActivitiesRepository
    ): MembersDetailFragment? {
        if (userModel == null) return null
        val userName = "${userModel.firstName} ${userModel.lastName}".trim().ifBlank { userModel.name }
        val fragment = MembersDetailFragment.newInstance(
            userName.toString(),
            userModel.email.toString(),
            userModel.dob.toString().substringBefore("T"),
            userModel.language.toString(),
            userModel.phoneNumber.toString(),
            (userModel.id?.let { activitiesRepository.getOfflineVisitCount(it) } ?: 0).toString(),
            (activitiesRepository.getLastVisit(userModel.name ?: "")?.let { dateFormatter.get()?.format(Date(it)) } ?: "No logout record found"),
            "${userModel.firstName} ${userModel.lastName}",
            userModel.level.toString(),
            userModel.userImage
        )
        return fragment
    }

}
