package org.ole.planet.myplanet.utilities

import android.content.Context
import android.widget.Toast
import io.realm.Realm
import java.text.Normalizer
import java.util.regex.Pattern
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.sync.LoginSyncManager
import org.ole.planet.myplanet.ui.sync.LoginActivity

object AuthUtils {
    private val specialCharPattern = Pattern.compile(
        ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
    )

    fun validateUsername(context: Context, username: String, realm: Realm? = null): String? {
        val firstChar = username.firstOrNull()
        return when {
            username.isEmpty() -> context.getString(R.string.username_cannot_be_empty)
            username.contains(" ") -> context.getString(R.string.invalid_username)
            firstChar != null && !firstChar.isDigit() && !firstChar.isLetter() ->
                context.getString(R.string.must_start_with_letter_or_number)
            hasInvalidCharacters(username) ||
                specialCharPattern.matcher(username).matches() ||
                hasDiacriticCharacters(username) ->
                context.getString(R.string.only_letters_numbers_and_are_allowed)
            realm != null && RealmUserModel.isUserExists(realm, username) ->
                context.getString(R.string.username_taken)
            else -> null
        }
    }

    private fun hasInvalidCharacters(input: String) =
        input.any { it != '_' && it != '.' && it != '-' && !it.isDigit() && !it.isLetter() }

    private fun hasDiacriticCharacters(input: String): Boolean {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return !normalized.codePoints().allMatch { code ->
            Character.isLetterOrDigit(code) ||
                code == '.'.code ||
                code == '-'.code ||
                code == '_'.code
        }
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

        LoginSyncManager.instance.login(name, password, object : SyncListener {
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
