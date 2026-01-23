package org.ole.planet.myplanet.utils

import android.util.Log
import android.widget.Toast
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.sync.LoginSyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity

object AuthUtils {
    private const val TAG = "AUTH_LOGIN"

    suspend fun validateUsername(
        username: String,
        userRepository: UserRepository,
    ): String? {
        return userRepository.validateUsername(username)
    }

    fun login(activity: LoginActivity, name: String?, password: String?) {
        Log.d(TAG, "[AuthUtils] login() called for username: $name")
        if (activity.forceSyncTrigger()) {
            Log.d(TAG, "[AuthUtils] forceSyncTrigger returned true, returning early")
            return
        }

        val settings = activity.settings
        SecurePrefs.saveCredentials(activity, settings, name, password)

        Log.d(TAG, "[AuthUtils] Attempting local authentication for: $name")
        val isLoggedIn = activity.authenticateUser(settings, name, password, false)
        Log.d(TAG, "[AuthUtils] Local authentication result: $isLoggedIn")
        if (isLoggedIn) {
            Log.d(TAG, "[AuthUtils] Local auth succeeded, calling onLogin() - LOGIN #1")
            Toast.makeText(activity, activity.getString(R.string.welcome, name), Toast.LENGTH_SHORT).show()
            activity.onLogin()
            activity.saveUsers(name, password, "member")
            Log.d(TAG, "[AuthUtils] Returning after local auth success")
            return
        }

        Log.d(TAG, "[AuthUtils] Local auth failed, starting LoginSyncManager.login()")
        LoginSyncManager.instance.login(name, password, object : OnSyncListener {
            override fun onSyncStarted() {
                Log.d(TAG, "[AuthUtils] onSyncStarted callback")
                activity.customProgressDialog.setText(activity.getString(R.string.please_wait))
                activity.customProgressDialog.show()
            }

            override fun onSyncComplete() {
                Log.d(TAG, "[AuthUtils] onSyncComplete callback")
                activity.customProgressDialog.dismiss()
                val log = activity.authenticateUser(activity.settings, name, password, true)
                Log.d(TAG, "[AuthUtils] Post-sync authentication result: $log")
                if (log) {
                    Log.d(TAG, "[AuthUtils] Post-sync auth succeeded, calling onLogin() - LOGIN #2 (sync path)")
                    Toast.makeText(activity.applicationContext, activity.getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                    activity.onLogin()
                    activity.saveUsers(name, password, "member")
                } else {
                    Log.e(TAG, "[AuthUtils] Post-sync auth failed")
                    activity.alertDialogOkay(activity.getString(R.string.err_msg_login))
                }
                activity.syncIconDrawable.stop()
                activity.syncIconDrawable.selectDrawable(0)
            }

            override fun onSyncFailed(msg: String?) {
                Log.e(TAG, "[AuthUtils] onSyncFailed: $msg")
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                activity.customProgressDialog.dismiss()
                activity.syncIconDrawable.stop()
                activity.syncIconDrawable.selectDrawable(0)
            }
        })
    }
}
