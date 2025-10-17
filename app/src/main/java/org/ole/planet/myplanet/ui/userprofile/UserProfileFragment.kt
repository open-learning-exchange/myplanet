package org.ole.planet.myplanet.ui.userprofile

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import java.lang.String.format
import java.util.ArrayList
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.LinkedList
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.R.array.language
import org.ole.planet.myplanet.R.array.subject_level
import org.ole.planet.myplanet.databinding.EditProfileDialogBinding
import org.ole.planet.myplanet.databinding.FragmentUserProfileBinding
import org.ole.planet.myplanet.databinding.RowStatBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class UserProfileFragment : Fragment() {
    private var _binding: FragmentUserProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var rowStatBinding: RowStatBinding
    private lateinit var settings: SharedPreferences

    companion object {
        private const val TAG = "UserProfileFragment"
    }
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private lateinit var mRealm: Realm
    private var model: RealmUserModel? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private var selectedGender: String? = null
    var selectedLevel: String? = null
    var selectedLanguage: String? = null
    var date: String? = null
    private var photoURI: Uri? = null
    private lateinit var captureImageLauncher: ActivityResultLauncher<Uri>
    private lateinit var requestCameraLauncher: ActivityResultLauncher<String>

    override fun onDestroy() {
        if (this::mRealm.isInitialized) {
            mRealm.close()
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val uri = result.data?.data ?: return@registerForActivityResult
                photoURI  = uri
                startIntent(photoURI)
                Glide.with(this)
                    .load(uri)
                    .into(binding.image)
            }
        }

        captureImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                startIntent(photoURI)
                binding.image.setImageURI(photoURI)
            }
        }

        requestCameraLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                takePhoto()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        .setTitle(R.string.permission_required)
                        .setMessage(R.string.camera_permission_required)
                        .setPositiveButton(R.string.settings) { dialog, _ ->
                            dialog.dismiss()
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri: Uri = Uri.fromParts("package", requireContext().packageName, null)
                            intent.data = uri
                            startActivity(intent)
                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .show()
                } else {
                    Utilities.toast(requireContext(), "camera permission is required.")
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserProfileBinding.inflate(inflater, container, false)
        initializeDependencies()
        binding.btProfilePic.setOnClickListener { searchForPhoto() }
        model = userProfileDbHandler.userModel

        setupProfile()
        loadProfileImage()

        binding.btEditProfile.setOnClickListener { openEditProfileDialog() }
        configureGuestView()
        setupStatsRecycler()

        return binding.root
    }

    private fun initializeDependencies() {
        settings = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        mRealm = databaseService.realmInstance
        binding.rvStat.layoutManager = LinearLayoutManager(activity)
        binding.rvStat.isNestedScrollingEnabled = false
    }

    private fun setupProfile() {
        binding.txtName.text = if (!model?.firstName.isNullOrEmpty() && !model?.lastName.isNullOrEmpty()) {
            "${model?.firstName} ${model?.lastName}"
        } else {
            model?.name ?: ""
        }
        binding.txtEmail.text = getString(R.string.two_strings, getString(R.string.email_colon), Utilities.checkNA(model?.email))
        val dob = if (TextUtils.isEmpty(model?.dob)) getString(R.string.n_a) else TimeUtils.getFormattedDate(model?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        binding.txtDob.text = getString(R.string.two_strings, getString(R.string.date_of_birth), dob)
        binding.txtGender.text = getString(R.string.gender_colon, Utilities.checkNA(model?.gender))
        binding.txtLanguage.text = getString(R.string.two_strings, getString(R.string.language_colon), Utilities.checkNA(model?.language))
        binding.txtLevel.text = getString(R.string.level_colon, Utilities.checkNA(model?.level))
    }

    private fun loadProfileImage() {
        val binding = _binding ?: return
        val profileImageUrl = model?.userImage

        if (profileImageUrl.isNullOrBlank()) {
            binding.image.setImageResource(R.drawable.profile)
            return
        }

        if (!isAdded) return

        Glide.with(this)
            .load(profileImageUrl)
            .apply(RequestOptions().placeholder(R.drawable.profile).error(R.drawable.profile))
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (!isAdded) {
                        return true
                    }
                    val currentBinding = _binding ?: return true
                    currentBinding.image.apply {
                        visibility = View.VISIBLE
                        setImageResource(R.drawable.profile)
                    }
                    return true
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }
            })
            .into(binding.image)
    }

    private fun openEditProfileDialog() {
        val dialog = Dialog(requireContext()).apply { setCancelable(false) }
        val binding = EditProfileDialogBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        populateUserFields(binding)
        setupLanguageSpinner(binding)
        setupLevelSpinner(binding)
        setupGender(binding)
        setupDatePicker(binding)
        setupSaveButton(dialog, binding)

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun populateUserFields(binding: EditProfileDialogBinding) {
        binding.firstName.setText(model?.firstName)
        binding.middleName.setText(model?.middleName)
        binding.lastName.setText(model?.lastName)
        binding.email.setText(model?.email)
        binding.phoneNumber.setText(model?.phoneNumber)
        val dobText = if (TextUtils.isEmpty(model?.dob)) {
            getString(R.string.n_a)
        } else {
            TimeUtils.getFormattedDate(model?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        }
        binding.dateOfBirth.text = dobText
    }

    private fun setupLanguageSpinner(binding: EditProfileDialogBinding) {
        val languages = resources.getStringArray(language)
        val languageList: MutableList<String?> = ArrayList(listOf(*languages))
        languageList.add(0, getString(R.string.language))
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, languageList)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        binding.language.adapter = adapter
        model?.language?.let { lang ->
            val position = languageList.indexOf(lang)
            binding.language.setSelection(if (position >= 0) position else 0)
        } ?: binding.language.setSelection(0)
        binding.language.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLanguage = parent.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupLevelSpinner(binding: EditProfileDialogBinding) {
        val levels = resources.getStringArray(subject_level).toMutableList().apply { remove("All") }
        levels.add(0, getString(R.string.select_level))
        selectedLevel = Utilities.checkNA(model?.level)
        val levelAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, levels)
        levelAdapter.setDropDownViewResource(R.layout.spinner_item)
        binding.level.adapter = levelAdapter

        val levelPosition = levels.indexOf(selectedLevel)
        if (levelPosition > 0) binding.level.setSelection(levelPosition) else binding.level.setSelection(0)

        binding.level.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedLevel = if (position == 0) "" else levels[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupGender(binding: EditProfileDialogBinding) {
        when (model?.gender?.lowercase(Locale.US)) {
            "male" -> binding.rbMale.isChecked = true
            "female" -> binding.rbFemale.isChecked = true
        }
    }

    private fun setupDatePicker(binding: EditProfileDialogBinding) {
        binding.dateOfBirth.setOnClickListener {
            val now = Calendar.getInstance()
            var dobPrevious = Calendar.getInstance()
            val previousSelectedDate = date ?: model?.dob
            if(!previousSelectedDate.isNullOrEmpty()){
                val instant = TimeUtils.parseInstantFromString(previousSelectedDate)
                instant?.let {
                    dobPrevious = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = it.toEpochMilli()
                    }
                }
            }

            val dpd = DatePickerDialog(
                requireContext(),
                { _, year, monthOfYear, dayOfMonth ->
                    val calendar = Calendar.getInstance()
                    calendar.set(year, monthOfYear, dayOfMonth)
                    val dobMillis = calendar.timeInMillis
                    val dobFormatted = TimeUtils.getFormattedDate(dobMillis)

                    date = format(Locale.US, "%04d-%02d-%02dT00:00:00.000Z", year, monthOfYear + 1, dayOfMonth)
                    binding.dateOfBirth.text = dobFormatted
                },
                dobPrevious.get(Calendar.YEAR),
                dobPrevious.get(Calendar.MONTH),
                dobPrevious.get(Calendar.DAY_OF_MONTH)
            )
            dpd.datePicker.maxDate = now.timeInMillis
            dpd.show()
        }
    }

    private fun setupSaveButton(dialog: Dialog, binding: EditProfileDialogBinding) {
        binding.btnSave.setOnClickListener {
            if (!validateInputs(binding)) {
                return@setOnClickListener
            }

            selectedGender = when {
                binding.rbMale.isChecked -> "male"
                binding.rbFemale.isChecked -> "female"
                else -> selectedGender
            }

            val firstName = binding.firstName.text.toString()
            val lastName = binding.lastName.text.toString()
            val middleName = binding.middleName.text.toString()
            val email = binding.email.text.toString()
            val phoneNumber = binding.phoneNumber.text.toString()
            val dob = date ?: model?.dob

            val realm = databaseService.realmInstance
            val userId = settings.getString("userId", "")

            RealmUserModel.updateUserDetails(
                realm, userId, firstName, lastName, middleName, email, phoneNumber, selectedLevel,
                selectedLanguage.takeUnless { it == getString(R.string.language) }, selectedGender, dob
            ) {
                mRealm.refresh()
                val updatedModel = userProfileDbHandler.userModel
                model = updatedModel
                updateUIWithUserData(updatedModel)
                realm.close()
                dialog.dismiss()
            }
        }
    }

    private fun validateInputs(binding: EditProfileDialogBinding): Boolean {
        return when {
            TextUtils.isEmpty(binding.firstName.text.toString().trim()) -> {
                binding.firstName.error = getString(R.string.compulsory_first_name)
                false
            }
            TextUtils.isEmpty(binding.lastName.text.toString().trim()) -> {
                binding.lastName.error = getString(R.string.compulsory_last_name)
                false
            }
            TextUtils.isEmpty(binding.email.text.toString().trim()) -> {
                binding.email.error = getString(R.string.compulsory_email)
                false
            }
            TextUtils.isEmpty(binding.phoneNumber.text.toString().trim()) -> {
                binding.phoneNumber.error = getString(R.string.compulsory_phone_number)
                false
            }
            getString(R.string.birth_date) == binding.dateOfBirth.text.toString() -> {
                binding.dateOfBirth.error = getString(R.string.compulsory_date_of_birth)
                false
            }
            binding.rdGender.checkedRadioButtonId == -1 -> {
                Snackbar.make(binding.root, getString(R.string.gender_not_picked), Snackbar.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }
    private fun configureGuestView() {
        if (model?.id?.startsWith("guest") == true) {
            binding.btEditProfile.visibility = View.GONE
            binding.btProfilePic.visibility = View.GONE
        }
    }

    private fun createStatsMap(): LinkedHashMap<String, String?> {
        return linkedMapOf(
            getString(R.string.community_name) to Utilities.checkNA(model?.planetCode),
            getString(R.string.last_login) to userProfileDbHandler.lastVisit?.let { TimeUtils.getRelativeTime(it) },
            getString(R.string.total_visits_overall) to userProfileDbHandler.offlineVisits.toString(),
            getString(R.string.most_opened_resource) to Utilities.checkNA(userProfileDbHandler.maxOpenedResource),
            getString(R.string.number_of_resources_opened) to Utilities.checkNA(userProfileDbHandler.numberOfResourceOpen)
        )
    }

    private fun setupStatsRecycler() {
        val map = createStatsMap()
        val keys = LinkedList(map.keys)
        binding.rvStat.adapter = object : RecyclerView.Adapter<ViewHolderRowStat>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderRowStat {
                rowStatBinding = RowStatBinding.inflate(LayoutInflater.from(activity), parent, false)
                return ViewHolderRowStat(rowStatBinding)
            }

            override fun onBindViewHolder(holder: ViewHolderRowStat, position: Int) {
                rowStatBinding.tvTitle.text = keys[position]
                rowStatBinding.tvTitle.visibility = View.VISIBLE
                rowStatBinding.tvDescription.text = map[keys[position]]
                if (position % 2 == 0) {
                    rowStatBinding.root.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.user_profile_background
                        )
                    )
                }
            }

            override fun getItemCount(): Int {
                return keys.size
            }
        }
    }

    private fun searchForPhoto() {
        val options = arrayOf(getString(R.string.capture_image), getString(R.string.select_gallery))
        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
        builder.setTitle(getString(R.string.choose_an_option))
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        builder.setAdapter(adapter) { _, which ->
            when (which) {
                0 -> takePhoto()
                1 -> pickFromGallery()
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.listView.children.forEach { item ->
                (item as TextView).setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
            }
        }

        dialog.show()
    }

    private fun pickFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED){
            requestCameraLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "Photo_${UUID.randomUUID()}")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ole/photo")
            }
        }
        photoURI = requireActivity().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        photoURI?.let { captureImageLauncher.launch(it) }
    }

    private fun startIntent(uri: Uri?) {
        var path: String? = null
        path = uri?.toString()

        mRealm.let {
            if (!it.isInTransaction) {
                it.beginTransaction()
            }
            model?.userImage = path
            model?.isUpdated = true
            it.commitTransaction()
        }
    }

    private fun updateUIWithUserData(model: RealmUserModel?) {
        model?.let {
            binding.txtName.text = String.format("%s %s %s", it.firstName, it.middleName, it.lastName)
            binding.txtEmail.text = getString(R.string.two_strings, getString(R.string.email_colon), Utilities.checkNA(it.email))
            val dob = if (TextUtils.isEmpty(it.dob)) "N/A" else TimeUtils.getFormattedDate(it.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            binding.txtDob.text = getString(R.string.two_strings, getString(R.string.date_of_birth), dob)
            binding.txtGender.text = getString(R.string.gender_colon, Utilities.checkNA(it.gender))
            binding.txtLanguage.text = getString(R.string.two_strings, getString(R.string.language_colon), Utilities.checkNA(it.language))
            binding.txtLevel.text = getString(R.string.level_colon, Utilities.checkNA(it.level))
        }
    }

    inner class ViewHolderRowStat(rowStatBinding: RowStatBinding) : RecyclerView.ViewHolder(rowStatBinding.root)

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
