package org.ole.planet.myplanet.ui.userprofile

import android.app.Activity.RESULT_OK
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import io.realm.Realm
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
import java.lang.String.format
import java.util.*

class UserProfileFragment : Fragment() {
    private lateinit var fragmentUserProfileBinding: FragmentUserProfileBinding
    private lateinit var rowStatBinding: RowStatBinding
    private lateinit var handler: UserProfileDbHandler
    private lateinit var settings: SharedPreferences
    private lateinit var realmService: DatabaseService
    private lateinit var mRealm: Realm
    private var model: RealmUserModel? = null
    private var imageUrl = ""
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private var selectedGender: String? = null
    var selectedLevel: String? = null
    var selectedLanguage: String? = null
    var date: String? = null

    override fun onDestroy() {
        super.onDestroy()
        if (this::mRealm.isInitialized) {
            mRealm.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val url = result.data?.data
                imageUrl = url.toString()

                mRealm.let {
                    if (!it.isInTransaction) {
                        it.beginTransaction()
                    }
                    val path = FileUtils.getRealPathFromURI(requireActivity(), url)
                    model?.userImage = path
                    model?.isUpdated = true
                    it.commitTransaction()
                }
                fragmentUserProfileBinding.image.setImageURI(url)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentUserProfileBinding = FragmentUserProfileBinding.inflate(inflater, container, false)
        settings = requireContext().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        handler = UserProfileDbHandler(requireContext())
        realmService = DatabaseService()
        mRealm = realmService.realmInstance
        fragmentUserProfileBinding.rvStat.layoutManager = LinearLayoutManager(activity)
        fragmentUserProfileBinding.rvStat.isNestedScrollingEnabled = false

        fragmentUserProfileBinding.btProfilePic.setOnClickListener { searchForPhoto() }
        model = handler.userModel
        fragmentUserProfileBinding.txtName.text = if (!model?.firstName.isNullOrEmpty() && !model?.lastName.isNullOrEmpty()) {
            "${model?.firstName} ${model?.lastName}"
        } else {
            model?.name ?: ""
        }
        fragmentUserProfileBinding.txtEmail.text = getString(R.string.two_strings, getString(R.string.email_colon), Utilities.checkNA(model?.email))
        val dob = if (TextUtils.isEmpty(model?.dob)) "N/A" else TimeUtils.getFormatedDate(model?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        fragmentUserProfileBinding.txtDob.text = getString(R.string.two_strings, getString(R.string.date_of_birth), dob)
        fragmentUserProfileBinding.txtGender.text = getString(R.string.gender_colon, Utilities.checkNA(model?.gender))
        fragmentUserProfileBinding.txtLanguage.text = getString(R.string.two_strings, getString(R.string.language_colon), Utilities.checkNA(model?.language))
        fragmentUserProfileBinding.txtLevel.text = getString(R.string.level_colon, Utilities.checkNA(model?.level))

        model?.userImage.let {
            Glide.with(requireContext())
                .load(it)
                .apply(RequestOptions().placeholder(R.drawable.profile).error(R.drawable.profile))
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                        fragmentUserProfileBinding.image.setImageResource(R.drawable.profile)
                        return false
                    }

                    override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        return false
                    }

                })
                .into(fragmentUserProfileBinding.image)
        }

        fragmentUserProfileBinding.btEditProfile.setOnClickListener {
            val dialog = Dialog(requireContext())
            dialog.setCancelable(false)
            val editProfileDialogBinding = EditProfileDialogBinding.inflate(LayoutInflater.from(requireContext()))
            dialog.setContentView(editProfileDialogBinding.getRoot())
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            editProfileDialogBinding.firstName.setText(model?.firstName)
            editProfileDialogBinding.middleName.setText(model?.middleName)
            editProfileDialogBinding.lastName.setText(model?.lastName)
            editProfileDialogBinding.email.setText(model?.email)
            editProfileDialogBinding.phoneNumber.setText(model?.phoneNumber)
            val dob1 =
                if (TextUtils.isEmpty(model?.dob)) {
                    "N/A"
                } else {
                    TimeUtils.getFormatedDate(model?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                }
            editProfileDialogBinding.dateOfBirth.text = dob1
            val languages = resources.getStringArray(language)
            val languageList: MutableList<String?> = ArrayList(listOf(*languages))
            languageList.add(0, "Language")
            val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, languageList)
            adapter.setDropDownViewResource(R.layout.spinner_item)
            editProfileDialogBinding.language.adapter = adapter
            if (model?.language != null) {
                val language = resources.getStringArray(language)
                val languageLists = listOf(*language)
                val languagePosition = languageLists.indexOf(model?.language)
                if (languagePosition >= 0) {
                    editProfileDialogBinding.language.setSelection(languagePosition + 1)
                }
            } else {
                editProfileDialogBinding.language.setSelection(0)
            }
            editProfileDialogBinding.language.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    selectedLanguage = parent.getItemAtPosition(position).toString()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
            val levels = resources.getStringArray(subject_level)
            val levelList: MutableList<String?> = ArrayList(listOf(*levels))
            levelList.remove("All")
            levelList.add(0, "Level")
            val levelAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item, levelList)
            levelAdapter.setDropDownViewResource(R.layout.spinner_item)
            editProfileDialogBinding.level.adapter = levelAdapter
            if (model?.level != null) {
                val level = resources.getStringArray(subject_level)
                val leveList = level.filter { it != "All" }
                val levelPosition = leveList.indexOf(model?.level)
                if (levelPosition >= 0) {
                    editProfileDialogBinding.level.setSelection(levelPosition + 1)
                } else {
                    editProfileDialogBinding.level.setSelection(0)
                }
            } else {
                editProfileDialogBinding.level.setSelection(0)
            }

            editProfileDialogBinding.level.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                    selectedLevel = parent.getItemAtPosition(position).toString()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
            if ("male".equals(model?.gender, ignoreCase = true)) {
                editProfileDialogBinding.rbMale.isChecked = true
            } else if ("female".equals(model?.gender, ignoreCase = true)) {
                editProfileDialogBinding.rbFemale.isChecked = true
            }
            editProfileDialogBinding.dateOfBirth.setOnClickListener {
                val now: Calendar = Calendar.getInstance()
                val dpd = DatePickerDialog(requireContext(), { _, year, monthOfYear, dayOfMonth ->
                    val dob2 = format(Locale.US, "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                    date = format(Locale.US, "%04d-%02d-%02dT00:00:00.000Z", year, monthOfYear + 1, dayOfMonth)
                    editProfileDialogBinding.dateOfBirth.text = dob2 },
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
                )
                dpd.datePicker.maxDate = now.timeInMillis
                dpd.show()
            }
            editProfileDialogBinding.btnSave.setOnClickListener {
                if (TextUtils.isEmpty("${editProfileDialogBinding.firstName.text}".trim())) {
                    editProfileDialogBinding.firstName.error = resources.getString(R.string.compulsory_first_name)
                } else if (TextUtils.isEmpty("${editProfileDialogBinding.lastName.text}".trim())) {
                    editProfileDialogBinding.lastName.error = getString(R.string.compulsory_last_name)
                } else if (TextUtils.isEmpty("${editProfileDialogBinding.email.text}".trim())) {
                    editProfileDialogBinding.email.error = getString(R.string.compulsory_email)
                } else if (TextUtils.isEmpty("${editProfileDialogBinding.phoneNumber.text}".trim())) {
                    editProfileDialogBinding.phoneNumber.error = getString(R.string.compulsory_phone_number)
                } else if (resources.getString(R.string.birth_date) == "${editProfileDialogBinding.dateOfBirth.text}") {
                    editProfileDialogBinding.dateOfBirth.error = getString(R.string.compulsory_date_of_birth)
                } else if (editProfileDialogBinding.rdGender.checkedRadioButtonId == -1) {
                    Snackbar.make(editProfileDialogBinding.root, getString(R.string.gender_not_picked), Snackbar.LENGTH_SHORT).show()
                } else {
                    if (editProfileDialogBinding.rbMale.isChecked) {
                        selectedGender = "male"
                    } else if (editProfileDialogBinding.rbFemale.isChecked) {
                        selectedGender = "female"
                    }
                    val realm = Realm.getDefaultInstance()
                    val userId = settings.getString("userId", "")
                    RealmUserModel.updateUserDetails(
                        realm, userId, "${editProfileDialogBinding.firstName.text}",
                        "${editProfileDialogBinding.lastName.text}",
                        "${editProfileDialogBinding.middleName.text}",
                        "${editProfileDialogBinding.email.text}",
                        "${editProfileDialogBinding.phoneNumber.text}",
                        selectedLevel, selectedLanguage, selectedGender, date
                    ){
                        updateUIWithUserData(model)
                    }
                    realm.close()
                    dialog.dismiss()
                }
            }
            editProfileDialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }

        if (model?.id?.startsWith("guest") == true) {
            fragmentUserProfileBinding.btEditProfile.visibility = View.GONE
            fragmentUserProfileBinding.btProfilePic.visibility = View.GONE
        }

        val map = linkedMapOf(
            getString(R.string.community_name) to Utilities.checkNA(model?.planetCode!!),
            getString(R.string.last_login) to handler.lastVisit?.let { Utilities.getRelativeTime(it) },
            getString(R.string.total_visits_overall) to handler.offlineVisits.toString(),
            getString(R.string.most_opened_resource) to Utilities.checkNA(handler.maxOpenedResource),
            getString(R.string.number_of_resources_opened) to Utilities.checkNA(handler.numberOfResourceOpen)
        )

        val keys = LinkedList(map.keys)
        fragmentUserProfileBinding.rvStat.adapter = object : RecyclerView.Adapter<ViewHolderRowStat>() {
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

        return fragmentUserProfileBinding.root
    }

    private fun searchForPhoto() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun updateUIWithUserData(model: RealmUserModel?) {
        model?.let {
            fragmentUserProfileBinding.txtName.text = String.format("%s %s %s", it.firstName, it.middleName, it.lastName)
            fragmentUserProfileBinding.txtEmail.text = getString(R.string.two_strings, getString(R.string.email_colon), Utilities.checkNA(it.email))
            val dob = if (TextUtils.isEmpty(it.dob)) "N/A" else TimeUtils.getFormatedDate(it.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            fragmentUserProfileBinding.txtDob.text = getString(R.string.two_strings, getString(R.string.date_of_birth), dob)
            fragmentUserProfileBinding.txtGender.text = getString(R.string.gender_colon, Utilities.checkNA(it.gender))
            fragmentUserProfileBinding.txtLanguage.text = getString(R.string.two_strings, getString(R.string.language_colon), Utilities.checkNA(it.language))
            fragmentUserProfileBinding.txtLevel.text = getString(R.string.level_colon, Utilities.checkNA(it.level))
        }
    }

    inner class ViewHolderRowStat(rowStatBinding: RowStatBinding) : RecyclerView.ViewHolder(rowStatBinding.root)
}
