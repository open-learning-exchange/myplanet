package org.ole.planet.myplanet.utils

import android.widget.Toast
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.sync.LoginSyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity

object AuthUtils {
    suspend fun validateUsername(
        username: String,
        userRepository: UserRepository,
    ): String? {
        return userRepository.validateUsername(username)
    }

    suspend fun login(activity: LoginActivity, loginSyncManager: LoginSyncManager, name: String?, password: String?, isFastSync: Boolean = false) {
        if (activity.forceSyncTrigger()) return

        val settings = activity.settings
        withContext(Dispatchers.IO) {
            SecurePrefs.saveCredentials(activity, settings, name, password)
        }

        // When fastSync is on there may be no local users yet, so skip the offline-only path
        // and go straight to the server endpoint.
        if (!isFastSync) {
            val isLoggedIn = activity.authenticateUser(settings, name, password, false)
            if (isLoggedIn) {
                Toast.makeText(activity, activity.getString(R.string.welcome, name), Toast.LENGTH_SHORT).show()
                activity.onLogin()
                activity.saveUsers(name, password, "member")
                return
            }
        }

        val syncResult = suspendCancellableCoroutine<Boolean> { continuation ->
            loginSyncManager.login(name, password, object : OnSyncListener {
                override fun onSyncStarted() {
                    activity.customProgressDialog.setText(activity.getString(R.string.please_wait))
                    activity.customProgressDialog.show()
                }

                override fun onSyncComplete() {
                    activity.customProgressDialog.dismiss()
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onSyncFailed(msg: String?) {
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                    activity.customProgressDialog.dismiss()
                    activity.syncIconDrawable.stop()
                    activity.syncIconDrawable.selectDrawable(0)
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            })
        }

        if (syncResult) {
            // fastSync: user was just authenticated against the server and saved locally,
            // so authenticate without the manager restriction.
            val log = activity.authenticateUser(activity.settings, name, password, !isFastSync)
            if (log) {
                Toast.makeText(activity.applicationContext, activity.getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                activity.onLogin()
                activity.saveUsers(name, password, "member")
            } else {
                activity.alertDialogOkay(activity.getString(R.string.err_msg_login))
            }
            activity.syncIconDrawable.stop()
            activity.syncIconDrawable.selectDrawable(0)
        }
    }
}
