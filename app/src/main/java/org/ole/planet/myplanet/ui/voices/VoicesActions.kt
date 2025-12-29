package org.ole.planet.myplanet.ui.voices

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
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmVoices
import org.ole.planet.myplanet.model.RealmVoices.Companion.createVoices
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.member.MemberDetailFragment
import org.ole.planet.myplanet.utilities.JsonUtils

object VoicesActions {
    private val imagesToRemove = mutableSetOf<String>()

    data class EditDialogComponents(
        val view: View,
        val editText: EditText,
        val inputLayout: TextInputLayout,
        val imageLayout: ViewGroup
    )

    fun createEditDialogComponents(
        context: Context,
        listener: VoicesAdapter.OnVoicesItemClickListener?
    ): EditDialogComponents {
        val v = android.view.LayoutInflater.from(context).inflate(R.layout.alert_input, null)
        val tlInput = v.findViewById<TextInputLayout>(R.id.tl_input)
        val et = v.findViewById<EditText>(R.id.et_input)
        val llImage = v.findViewById<ViewGroup>(R.id.ll_alert_image)
        v.findViewById<View>(R.id.add_voice_image).setOnClickListener { listener?.addImage(llImage) }
        return EditDialogComponents(v, et, tlInput, llImage)
    }

    private fun loadExistingImages(context: Context, voice: RealmVoices?, imageLayout: ViewGroup) {
        imagesToRemove.clear()
        imageLayout.removeAllViews()

        val imageUrls = voice?.imageUrls
        if (!imageUrls.isNullOrEmpty()) {
            imageUrls.forEach { imageUrl ->
                try {
                    val imgObject = JsonUtils.gson.fromJson(imageUrl, JsonObject::class.java)
                    val path = JsonUtils.getString("imageUrl", imgObject)
                    if (path.isNotEmpty()) {
                        addImageWithRemoveIcon(context, path, imageLayout)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun addImageWithRemoveIcon(context: Context, imagePath: String, imageLayout: ViewGroup) {
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
        voice: RealmVoices?,
        realm: Realm,
        currentUser: RealmUserModel?,
        imageList: RealmList<String>?,
        listener: VoicesAdapter.OnVoicesItemClickListener?
    ) {
        val s = components.editText.text.toString().trim()
        if (s.isEmpty()) {
            components.inputLayout.error = dialog.context.getString(R.string.please_enter_message)
            return
        }
        if (isEdit) {
            editPost(realm, s, voice, imageList)
        } else {
            postReply(realm, s, voice, currentUser, imageList)
        }
        dialog.dismiss()
        listener?.clearImages()
        listener?.onDataChanged()
    }

    fun showEditAlert(
        context: Context,
        realm: Realm,
        id: String?,
        isEdit: Boolean,
        currentUser: RealmUserModel?,
        listener: VoicesAdapter.OnVoicesItemClickListener?,
        viewHolder: RecyclerView.ViewHolder,
        updateReplyButton: (RecyclerView.ViewHolder, RealmVoices?, Int) -> Unit = { _, _, _ -> }
    ) {
        val components = createEditDialogComponents(context, listener)
        val message = components.view.findViewById<TextView>(R.id.cust_msg)
        message.text = context.getString(if (isEdit) R.string.edit_post else R.string.reply)
        val icon = components.view.findViewById<ImageView>(R.id.alert_icon)
        icon.setImageResource(R.drawable.ic_edit)

        val voice = realm.where(RealmVoices::class.java).equalTo("id", id).findFirst()
        if (isEdit) {
            components.editText.setText(context.getString(R.string.message_placeholder, voice?.message))
            loadExistingImages(context, voice, components.imageLayout)
        }
        val dialog = AlertDialog.Builder(context, R.style.ReplyAlertDialog)
            .setView(components.view)
            .setPositiveButton(R.string.button_submit, null)
            .setNegativeButton(R.string.cancel) { d, _ -> d.dismiss() }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val currentImageList = listener?.getCurrentImageList()
            handlePositiveButton(dialog, isEdit, components, voice, realm, currentUser, currentImageList, listener)
            updateReplyButton(viewHolder,voice,viewHolder.bindingAdapterPosition)
        }
    }

    private fun postReply(
        realm: Realm,
        s: String?,
        voice: RealmVoices?,
        currentUser: RealmUserModel?,
        imageList: RealmList<String>?
    ) {
        val shouldCommit = !realm.isInTransaction
        if (shouldCommit) realm.beginTransaction()
        val map = HashMap<String?, String>()
        map["message"] = s ?: ""
        map["viewableBy"] = voice?.viewableBy ?: ""
        map["viewableId"] = voice?.viewableId ?: ""
        map["replyTo"] = voice?.id ?: ""
        map["messageType"] = voice?.messageType ?: ""
        map["messagePlanetCode"] = voice?.messagePlanetCode ?: ""
        map["viewIn"] = voice?.viewIn ?: ""
        currentUser?.let { createVoices(map, realm, it, imageList, true) }
        if (shouldCommit) realm.commitTransaction()
    }

    private fun editPost(realm: Realm, s: String, voice: RealmVoices?, imageList: RealmList<String>?) {
        if (s.isEmpty()) return
        if (!realm.isInTransaction) realm.beginTransaction()

        if (imagesToRemove.isNotEmpty()) {
            voice?.imageUrls?.let { imageUrls ->
                val updatedUrls = imageUrls.filter { imageUrlJson ->
                    try {
                        val imgObject = JsonUtils.gson.fromJson(imageUrlJson, JsonObject::class.java)
                        val path = JsonUtils.getString("imageUrl", imgObject)
                        !imagesToRemove.contains(path)
                    } catch (_: Exception) {
                        true
                    }
                }
                voice.imageUrls?.clear()
                voice.imageUrls?.addAll(updatedUrls)
            }
            imagesToRemove.clear()
        }

        imageList?.forEach { voice?.imageUrls?.add(it) }
        voice?.updateMessage(s)
        realm.commitTransaction()
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
        realm: Realm,
        voice: RealmVoices?,
        list: MutableList<RealmVoices?>,
        teamName: String,
        listener: VoicesAdapter.OnVoicesItemClickListener? = null
    ) {
        val ar = JsonUtils.gson.fromJson(voice?.viewIn, JsonArray::class.java)
        if (!realm.isInTransaction) realm.beginTransaction()
        val position = list.indexOf(voice)
        if (position != -1) {
            list.removeAt(position)
        }
        if (teamName.isNotEmpty() || ar.size() < 2) {
            voice?.let { voiceItem ->
                deleteChildPosts(realm, voiceItem.id, list)

                val managedVoice = if (voiceItem.isManaged) {
                    voiceItem
                } else {
                    realm.where(RealmVoices::class.java)
                        .equalTo("id", voiceItem.id)
                        .findFirst()
                }
                
                managedVoice?.deleteFromRealm()
            }
        } else {
            voice?.let { voiceItem ->
                val filtered = JsonArray().apply {
                    ar.forEach { elem ->
                        if (!elem.asJsonObject.has("sharedDate")) {
                            add(elem)
                        }
                    }
                }
                
                val managedVoice = if (voiceItem.isManaged) {
                    voiceItem
                } else {
                    realm.where(RealmVoices::class.java)
                        .equalTo("id", voiceItem.id)
                        .findFirst()
                }
                
                managedVoice?.viewIn = JsonUtils.gson.toJson(filtered)
            }
        }
        realm.commitTransaction()
        listener?.onDataChanged()
    }

    private fun deleteChildPosts(
        realm: Realm,
        parentId: String?,
        list: MutableList<RealmVoices?>
    ) {
        if (parentId == null) return
        val children = realm.where(RealmVoices::class.java)
            .equalTo("replyTo", parentId)
            .findAll()
        children.forEach { child ->
            deleteChildPosts(realm, child.id, list)
            val idx = list.indexOf(child)
            if (idx != -1) list.removeAt(idx)
            child.deleteFromRealm()
        }
    }
}
