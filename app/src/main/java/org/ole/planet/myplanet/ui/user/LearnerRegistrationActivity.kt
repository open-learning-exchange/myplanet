package org.ole.planet.myplanet.ui.user

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.callback.OnChangedListener
import org.ole.planet.myplanet.databinding.ActivityLearnerRegistrationBinding
import org.ole.planet.myplanet.model.LearnerRegistrationInfo
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utils.DialogUtils.CustomProgressDialog
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class LearnerRegistrationActivity : BaseActivity() {

    @Inject
    override lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    override lateinit var dispatcherProvider: DispatcherProvider

    private val viewModel: LearnerRegistrationViewModel by viewModels()

    private lateinit var binding: ActivityLearnerRegistrationBinding
    var dob: String = ""
    var guest: Boolean = false
    private var usernameWatcher: TextWatcher? = null
    private var passwordWatcher: TextWatcher? = null
    private var rePasswordWatcher: TextWatcher? = null
    private var emailWatcher: TextWatcher? = null


    private fun selectedGender(): String? = when {
        binding.male.isChecked -> "male"
        binding.female.isChecked -> "female"
        else -> null
    }

    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(
            this, { _, i, i1, i2 ->
                dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
                binding.txtDob.text = dob
            }, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH]
        )
        dpd.setTitle(getString(R.string.select_date_of_birth))
        dpd.datePicker.maxDate = now.timeInMillis
        dpd.show()
    }

    private fun collectRegistrationInfo(): LearnerRegistrationInfo {
        val info = LearnerRegistrationInfo(
            binding.etUsername.text.toString(),
            binding.etPassword.text.toString(),
            binding.etRePassword.text.toString(),
            binding.etFname.text.toString(),
            binding.etLname.text.toString(),
            binding.etMname.text.toString(),
            binding.etEmail.text.toString(),
            binding.spnLang.selectedItem.toString(),
            binding.spnLevel.selectedItem.toString(),
            binding.etPhone.text.toString(),
            dob,
            selectedGender()
        )
        return info
    }

    private fun validateRegistrationInfo(info: LearnerRegistrationInfo): Boolean {
        return when {
            info.password.isEmpty() -> {
                binding.etPassword.error = getString(R.string.please_enter_a_password)
                false
            }
            info.password != info.rePassword -> {
                binding.etRePassword.error = getString(R.string.password_doesn_t_match)
                false
            }
            info.email.isNotEmpty() && !Utilities.isValidEmail(info.email) -> {
                binding.etEmail.error = getString(R.string.invalid_email)
                false
            }
            info.gender == null -> {
                Utilities.toast(this, getString(R.string.please_select_gender))
                false
            }
            else -> true
        }
    }

    private fun addMember(info: LearnerRegistrationInfo) {
        val customProgressDialog = CustomProgressDialog(this).apply {
            setText(getString(R.string.creating_member_account))
            show()
        }

        lifecycleScope.launch {
            val result = viewModel.createMember(info)
            withContext(dispatcherProvider.main) {
                if (result.first) {
                    val userName = info.username
                    val securityCallback = OnChangedListener {
                        customProgressDialog.dismiss()
                        autoLoginNewMember(info.username, info.password)
                    }
                    startUpload("becomeMember", userName, securityCallback)

                    if (result.second == getString(R.string.not_connect_to_planet_created_user_offline)) {
                        Utilities.toast(this@LearnerRegistrationActivity, result.second)
                        securityCallback.onChanged()
                    }
                    Utilities.toast(this@LearnerRegistrationActivity, result.second)
                } else {
                    Utilities.toast(this@LearnerRegistrationActivity, result.second)
                    customProgressDialog.dismiss()
                    binding.btnSubmit.isEnabled = true
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearnerRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, binding.root)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val languages = resources.getStringArray(R.array.language)
        val lnAadapter = ArrayAdapter(this, R.layout.become_a_member_spinner_layout, languages)
        binding.spnLang.adapter = lnAadapter
        binding.txtDob.setOnClickListener {
            showDatePickerDialog()
        }
        val levels = resources.getStringArray(R.array.level)
        val lvAdapter  = ArrayAdapter(this, R.layout.become_a_member_spinner_layout, levels)
        binding.spnLevel.adapter = lvAdapter

        var username = intent.getStringExtra("username") ?: ""
        guest = intent.getBooleanExtra("guest", false)
        if (guest && username.isEmpty()) {
            username = sharedPrefManager.getUserName()
        }

        setupTextWatchers()

        lifecycleScope.launch {
            viewModel.usernameChecks.collect { check ->
                if (binding.etUsername.text.toString() != check.input) {
                    return@collect
                }

                if (check.error != null) {
                    binding.etUsername.error = check.error
                } else {
                    val lowercase = check.input.lowercase()
                    if (check.input != lowercase) {
                        binding.etUsername.setText(lowercase)
                        binding.etUsername.setSelection(lowercase.length)
                    }
                    binding.etUsername.error = null
                }
            }
        }

        if (guest) {
            binding.etUsername.setText(username)
            binding.etUsername.isFocusable = false
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        binding.btnSubmit.setOnClickListener {
            binding.btnSubmit.isEnabled = false
            val info = collectRegistrationInfo()
            lifecycleScope.launch {
                val error = viewModel.validateUsername(info.username)
                withContext(dispatcherProvider.main) {
                    if (error != null) {
                        binding.etUsername.error = error
                        binding.btnSubmit.isEnabled = true
                    } else if (validateRegistrationInfo(info)) {
                        addMember(info)
                    } else {
                        binding.btnSubmit.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        binding.etUsername.removeTextChangedListener(usernameWatcher)
        binding.etPassword.removeTextChangedListener(passwordWatcher)
        binding.etRePassword.removeTextChangedListener(rePasswordWatcher)
        binding.etEmail.removeTextChangedListener(emailWatcher)
        usernameWatcher = null
        passwordWatcher = null
        rePasswordWatcher = null
        emailWatcher = null
        super.onDestroy()
    }

    private fun autoLoginNewMember(username: String, password: String) {
        lifecycleScope.launch {
            viewModel.cleanupDuplicateUsers()

            sharedPrefManager.setNewLoginUsername(username)
            sharedPrefManager.setNewLoginPassword(password)

            val intent = Intent(this@LearnerRegistrationActivity, LoginActivity::class.java)

            if (guest) {
                intent.putExtra("guest", guest)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun setupTextWatchers() {
        usernameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""

                if (input.isEmpty()) {
                    binding.etUsername.error = null
                    return
                }

                viewModel.onUsernameChanged(input)
            }
        }
        binding.etUsername.addTextChangedListener(usernameWatcher)

        passwordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                if (s.toString().isEmpty()) {
                    binding.etRePassword.setText("")
                }
                validatePasswordMatch()
            }
        }
        binding.etPassword.addTextChangedListener(passwordWatcher)

        rePasswordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                validatePasswordMatch()
            }
        }
        binding.etRePassword.addTextChangedListener(rePasswordWatcher)

        emailWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val email = s?.toString() ?: ""
                if (email.isNotEmpty() && !Utilities.isValidEmail(email)) {
                    binding.etEmail.error = getString(R.string.email_invalid_format)
                } else {
                    binding.etEmail.error = null
                }
            }
        }
        binding.etEmail.addTextChangedListener(emailWatcher)
    }

    private fun validatePasswordMatch() {
        val password = binding.etPassword.text.toString()
        val rePassword = binding.etRePassword.text.toString()
        if (rePassword.isNotEmpty() && password != rePassword) {
            binding.etRePassword.error = getString(R.string.passwords_do_not_match)
        } else {
            binding.etRePassword.error = null
        }
    }
}
