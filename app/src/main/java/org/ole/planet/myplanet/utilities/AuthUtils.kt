package org.ole.planet.myplanet.utilities

import android.widget.Toast
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.sync.LoginSyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity

object AuthUtils {
    suspend fun validateUsername(
        username: String,
        userRepository: UserRepository,
    ): String? {
        return userRepository.validateUsername(username)
    }

    fun login(activity: LoginActivity, name: String?, password: String?) {
        if (activity.forceSyncTrigger()) return

        val settings = activity.settings
        SecurePrefs.saveCredentials(activity, settings, name, password)

        val isLoggedIn = activity.authenticateUser(settings, name, password, false)
        if (isLoggedIn) {
            Toast.makeText(activity, activity.getString(R.string.welcome, name), Toast.LENGTH_SHORT).show()
            activity.onLogin()
            activity.saveUsers(name, password, "member")
            return
        }

        LoginSyncManager.instance.login(name, password, object : OnSyncListener {
            override fun onSyncStarted() {
                activity.customProgressDialog.setText(activity.getString(R.string.please_wait))
                activity.customProgressDialog.show()
            }

            override fun onSyncComplete() {
                activity.customProgressDialog.dismiss()
                val log = activity.authenticateUser(activity.settings, name, password, true)
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

            override fun onSyncFailed(msg: String?) {
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                activity.customProgressDialog.dismiss()
                activity.syncIconDrawable.stop()
                activity.syncIconDrawable.selectDrawable(0)
            }
        })
    }
}
