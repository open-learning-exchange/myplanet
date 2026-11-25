package org.ole.planet.myplanet.ui.voices

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnNewsItemClickListener
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.repository.VoicesEditor
import org.ole.planet.myplanet.services.UserSessionManager

/**
 * The reply / edit-post dialog for voices. A DialogFragment rather than a bare AlertDialog so the
 * system recreates it after rotation, with the typed text restored from the dialog's saved view
 * state. It finds its [OnNewsItemClickListener] through its host (the voices fragment or
 * [ReplyActivity]), which is recreated alongside it.
 */
@AndroidEntryPoint
class VoicesEditDialogFragment : DialogFragment() {
    @Inject lateinit var voicesEditor: VoicesEditor
    @Inject lateinit var userSessionManager: UserSessionManager

    /** Row refresh for the adapter that opened the dialog; lost on recreation, where the listener's own refresh covers it. */
    var onUpdated: ((News?) -> Unit)? = null

    private lateinit var components: VoicesActions.EditDialogComponents
    private val imagesToRemove = mutableSetOf<String>()
    private var imagesBeforeDialog: List<String> = emptyList()
    private var news: News? = null

    private val newsId: String? get() = arguments?.getString(ARG_NEWS_ID)
    private val isEdit: Boolean get() = arguments?.getBoolean(ARG_IS_EDIT) == true
    private val listener: OnNewsItemClickListener?
        get() = (parentFragment as? OnNewsItemClickListener) ?: (activity as? OnNewsItemClickListener)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        components = VoicesActions.createEditDialogComponents(context, listener)
        components.binding.custMsg.text = getString(if (isEdit) R.string.edit_post else R.string.reply)
        components.binding.alertIcon.setImageResource(R.drawable.ic_edit)

        if (savedInstanceState == null) {
            imagesBeforeDialog = listener?.getCurrentImageList()?.toList().orEmpty()
        } else {
            imagesBeforeDialog = savedInstanceState.getStringArrayList(KEY_IMAGES_BEFORE).orEmpty()
            imagesToRemove += savedInstanceState.getStringArrayList(KEY_IMAGES_TO_REMOVE).orEmpty()
        }

        lifecycleScope.launch {
            news = newsId?.let { voicesEditor.getNewsById(it) }
            if (!isEdit) return@launch
            // On recreation the edited text comes back from saved view state; don't overwrite it.
            if (savedInstanceState == null) {
                listener?.clearImages()
                components.editText.setText(getString(R.string.message_placeholder, news?.message))
            }
            VoicesActions.loadExistingImages(context, news, components.imageLayout, imagesToRemove)
        }

        val dialog = AlertDialog.Builder(context, R.style.ReplyAlertDialog)
            .setView(components.view)
            .setPositiveButton(R.string.button_submit, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit(dialog) }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                listener?.restoreImages(imagesBeforeDialog)
                dismiss()
            }
        }
        return dialog
    }

    private fun submit(dialog: AlertDialog) {
        val listener = listener
        val imageList = listener?.getCurrentImageList()
        val onUpdated = onUpdated
        // The save runs after the dialog is dismissed, so it needs a scope that outlives this fragment.
        val host: LifecycleOwner = (parentFragment?.viewLifecycleOwner ?: activity) ?: return
        host.lifecycleScope.launch {
            val currentUser = userSessionManager.getUserModel()
            VoicesActions.handlePositiveButton(
                dialog, isEdit, components, news, voicesEditor, currentUser,
                imageList, listener, imagesToRemove
            ) { updatedNews -> onUpdated?.invoke(updatedNews ?: news) }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        listener?.restoreImages(imagesBeforeDialog)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(KEY_IMAGES_BEFORE, ArrayList(imagesBeforeDialog))
        outState.putStringArrayList(KEY_IMAGES_TO_REMOVE, ArrayList(imagesToRemove))
    }

    companion object {
        const val TAG = "VoicesEditDialog"
        private const val ARG_NEWS_ID = "news_id"
        private const val ARG_IS_EDIT = "is_edit"
        private const val KEY_IMAGES_BEFORE = "images_before"
        private const val KEY_IMAGES_TO_REMOVE = "images_to_remove"

        /**
         * Shows the dialog as a child of [host] (a Fragment or activity implementing
         * [OnNewsItemClickListener]) so it can find the host again after recreation.
         */
        fun show(host: OnNewsItemClickListener?, newsId: String?, isEdit: Boolean, onUpdated: ((News?) -> Unit)? = null) {
            val manager = when (host) {
                is Fragment -> host.childFragmentManager
                is FragmentActivity -> host.supportFragmentManager
                else -> return
            }
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            VoicesEditDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NEWS_ID, newsId)
                    putBoolean(ARG_IS_EDIT, isEdit)
                }
                this.onUpdated = onUpdated
            }.show(manager, TAG)
        }
    }
}
