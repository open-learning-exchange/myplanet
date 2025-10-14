package org.ole.planet.myplanet.ui.sync

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AuthHelper
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.Utilities.toast

sealed class GuestLoginOutcome {
    data class Created(val user: RealmUserModel) : GuestLoginOutcome()
    data class Existing(val user: RealmUserModel) : GuestLoginOutcome()
    data class Failure(val throwable: Throwable? = null) : GuestLoginOutcome()
}

fun LoginActivity.showGuestLoginDialog() {
    val binding = AlertGuestLoginBinding.inflate(LayoutInflater.from(this))
    binding.etUserName.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            val input = s.toString()
            val error = AuthHelper.validateUsername(this@showGuestLoginDialog, input)
            if (error != null) {
                binding.etUserName.error = error
            } else {
                val normalized = input.lowercase(Locale.ROOT)
                if (input != normalized) {
                    binding.etUserName.setText(normalized)
                    binding.etUserName.setSelection(normalized.length)
                }
                binding.etUserName.error = null
            }
        }

        override fun afterTextChanged(s: Editable) {}
    })
    val progressDialog = DialogUtils.getCustomProgressDialog(this)
    val dialog = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        .setTitle(R.string.btn_guest_login)
        .setView(binding.root)
        .setPositiveButton(R.string.login, null)
        .setNegativeButton(R.string.cancel) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        .create()
    dialog.show()
    val loginButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    var loginJob: Job? = null
    var suppressDismissCancel = false
    dialog.setOnDismissListener {
        if (!suppressDismissCancel && loginJob?.isActive == true) {
            loginJob?.cancel()
        }
        if (progressDialog.isShowing()) {
            progressDialog.dismiss()
        }
    }
    loginButton.setOnClickListener {
        val username = binding.etUserName.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val error = AuthHelper.validateUsername(this, username)
        if (error != null) {
            binding.etUserName.error = error
            return@setOnClickListener
        }

        loginButton.isEnabled = false
        progressDialog.setTitle(getString(R.string.login))
        progressDialog.setText(getString(R.string.please_wait))
        progressDialog.show()
        if (!lifecycleScope.coroutineContext.isActive) {
            progressDialog.dismiss()
            loginButton.isEnabled = true
            return@setOnClickListener
        }
        loginJob = lifecycleScope.launch {
            try {
                val outcome = try {
                    MainApplication.service.withRealmAsync { realm ->
                        val existingUser = realm.where(RealmUserModel::class.java)
                            .equalTo("name", username)
                            .findFirst()

                        when {
                            existingUser != null -> GuestLoginOutcome.Existing(realm.copyFromRealm(existingUser))
                            else -> {
                                val created = RealmUserModel.createGuestUser(username, realm, settings)
                                    ?.let { realm.copyFromRealm(it) }
                                if (created != null) {
                                    GuestLoginOutcome.Created(created)
                                } else {
                                    GuestLoginOutcome.Failure()
                                }
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    GuestLoginOutcome.Failure(throwable)
                }

                when (outcome) {
                    is GuestLoginOutcome.Created -> {
                        suppressDismissCancel = true
                        dialog.dismiss()
                        val model = outcome.user
                        saveUsers(model.name ?: username, "", "guest")
                        saveUserInfoPref(settings, "", model)
                        onLogin()
                    }

                    is GuestLoginOutcome.Existing -> {
                        suppressDismissCancel = true
                        dialog.dismiss()
                        val model = outcome.user
                        when {
                            model._id?.contains("guest") == true -> showGuestDialog(username)
                            model._id?.contains("org.couchdb.user:") == true -> showUserAlreadyMemberDialog(username)
                            else -> toast(this@showGuestLoginDialog, getString(R.string.unable_to_login))
                        }
                    }

                    is GuestLoginOutcome.Failure -> {
                        toast(this@showGuestLoginDialog, getString(R.string.unable_to_login))
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    suppressDismissCancel = false
                    loginJob = null
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss()
                    }
                    loginButton.isEnabled = true
                }
            }
        }
    }
}

