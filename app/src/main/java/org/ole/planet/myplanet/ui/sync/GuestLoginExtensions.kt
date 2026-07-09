package org.ole.planet.myplanet.ui.sync

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.AuthUtils
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.ole.planet.myplanet.utils.textChanges
import org.ole.planet.myplanet.utils.Utilities.toast

@OptIn(FlowPreview::class)
fun LoginActivity.showGuestLoginDialog(userRepository: UserRepository) {
    val binding = AlertGuestLoginBinding.inflate(LayoutInflater.from(this))
    val view: View = binding.root

    val job = binding.etUserName.textChanges()
        .onEach { s ->
            val input = s?.toString() ?: ""
            val lowercaseText = input.lowercase()
            if (input != lowercaseText) {
                binding.etUserName.setText(lowercaseText)
                binding.etUserName.setSelection(lowercaseText.length)
            }
        }
        .debounce(300)
        .onEach { s ->
            val input = s?.toString() ?: ""
            lifecycleScope.launch {
                val error = AuthUtils.validateUsername(input, userRepository)
                if (error != null) {
                    binding.etUserName.error = error
                } else {
                    binding.etUserName.error = null
                }
            }
        }
        .launchIn(lifecycleScope)
    val dialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        .setTitle(R.string.btn_guest_login)
        .setView(view)
        .setPositiveButton(R.string.login, null)
        .setNegativeButton(R.string.cancel, null)
        .create()
    dialog.setOnDismissListener { job.cancel() }
    dialog.show()
    val login = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
    login.setOnClickListener {
        val username = binding.etUserName.text.toString().trim { it <= ' ' }
        lifecycleScope.launch {
            val error = AuthUtils.validateUsername(username, userRepository)
            if (error == null) {
                val existingUser = userRepository.findUserByName(username)
                dialog.dismiss()
                if (existingUser != null) {
                    when {
                        existingUser._id?.contains("guest") == true -> showGuestDialog(username)
                        existingUser._id?.contains("org.couchdb.user:") == true -> showUserAlreadyMemberDialog(username)
                    }
                } else {
                    val model = userRepository.createGuestUser(username)
                    if (model == null) {
                        toast(this@showGuestLoginDialog, getString(R.string.unable_to_login))
                    } else {
                        saveUsers(username, "", "guest")
                        profileDbHandler.saveUserInfoPref("", model)
                        onLogin()
                    }
                }
            } else {
                binding.etUserName.error = error
            }
        }
    }
    cancel.setOnClickListener { dialog.dismiss() }
}
