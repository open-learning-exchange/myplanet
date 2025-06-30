package org.ole.planet.myplanet.ui.sync

import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Utilities.toast
import java.text.Normalizer
import java.util.regex.Pattern

private val SPECIAL_CHAR_PATTERN = Pattern.compile(
    ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
)

fun LoginActivity.showGuestLoginDialog() {
    try {
        mRealm = Realm.getDefaultInstance()
        mRealm.refresh()
        val binding = AlertGuestLoginBinding.inflate(LayoutInflater.from(this))
        val view: View = binding.root
        binding.etUserName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val input = s.toString()
                val error = validateGuestUsername(input)
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
            if (mRealm.isClosed) {
                mRealm = Realm.getDefaultInstance()
            }
            val username = binding.etUserName.text.toString().trim { it <= ' ' }
            val error = validateGuestUsername(username)
            if (error == null) {
                val existingUser = mRealm.where(RealmUserModel::class.java).equalTo("name", username).findFirst()
                dialog.dismiss()
                if (existingUser != null) {
                    when {
                        existingUser._id?.contains("guest") == true -> showGuestDialog(username)
                        existingUser._id?.contains("org.couchdb.user:") == true -> showUserAlreadyMemberDialog(username)
                    }
                } else {
                    val model = RealmUserModel.createGuestUser(username, mRealm, settings)?.let { mRealm.copyFromRealm(it) }
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
        cancel.setOnClickListener { dialog.dismiss() }
    } finally {
        if (!mRealm.isClosed) {
            mRealm.close()
        }
    }
}

private fun LoginActivity.validateGuestUsername(username: String): String? {
    if (TextUtils.isEmpty(username)) {
        return getString(R.string.username_cannot_be_empty)
    }
    val firstChar = username[0]
    if (!Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
        return getString(R.string.must_start_with_letter_or_number)
    }
    val normalizedText = Normalizer.normalize(username, Normalizer.Form.NFD)
    val hasInvalidCharacters = username.any {
        it != '_' && it != '.' && it != '-' && !Character.isDigit(it) && !Character.isLetter(it)
    }
    val hasSpecialCharacters = SPECIAL_CHAR_PATTERN.matcher(username).matches()
    val hasDiacriticCharacters = !normalizedText.codePoints().allMatch { codePoint ->
        Character.isLetterOrDigit(codePoint) ||
            codePoint == '.'.code ||
            codePoint == '-'.code ||
            codePoint == '_'.code
    }
    return if (hasInvalidCharacters || hasSpecialCharacters || hasDiacriticCharacters) {
        getString(R.string.only_letters_numbers_and_are_allowed)
    } else null
}

