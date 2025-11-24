package org.ole.planet.myplanet.ui.sync

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AuthHelper
import org.ole.planet.myplanet.utilities.Utilities.toast

fun LoginActivity.showGuestLoginDialog() {
    MainApplication.service.withRealm { realm ->
        realm.refresh()
        val binding = AlertGuestLoginBinding.inflate(LayoutInflater.from(this))
        val view: View = binding.root
        binding.etUserName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val input = s.toString()
                val error = AuthHelper.validateUsername(this@showGuestLoginDialog, input)
                if (error != null) {
                    binding.etUserName.error = error
                } else {
                    val lowercaseText = input.lowercase()
                    if (input != lowercaseText) {
                        binding.etUserName.setText(lowercaseText)
                        binding.etUserName.setSelection(lowercaseText.length)
                    }
                    binding.etUserName.error = null
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(R.string.btn_guest_login)
            .setView(view)
            .setPositiveButton(R.string.login, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        val login = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        login.setOnClickListener {
            MainApplication.service.withRealm { loginRealm ->
                val username = binding.etUserName.text.toString().trim { it <= ' ' }
                val error = AuthHelper.validateUsername(this@showGuestLoginDialog, username)
                if (error == null) {
                    val existingUser = loginRealm.where(RealmUserModel::class.java).equalTo("name", username).findFirst()
                    dialog.dismiss()
                    if (existingUser != null) {
                        when {
                            existingUser._id?.contains("guest") == true -> showGuestDialog(username)
                            existingUser._id?.contains("org.couchdb.user:") == true -> showUserAlreadyMemberDialog(username)
                        }
                    } else {
                        val model = RealmUserModel.createGuestUser(username, loginRealm, settings)?.let { loginRealm.copyFromRealm(it) }
                        if (model == null) {
                            toast(this, getString(R.string.unable_to_login))
                        } else {
                            saveUsers(username, "", "guest")
                            saveUserInfoPref(settings, "", model)
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
}
