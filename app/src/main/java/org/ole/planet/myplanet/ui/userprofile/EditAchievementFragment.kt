package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import android.widget.Toolbar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import java.util.Calendar
import java.util.Locale
import kotlin.Array
import kotlin.Int
import kotlin.String
import kotlin.arrayOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseContainerFragment
import org.ole.planet.myplanet.databinding.AlertAddAttachmentBinding
import org.ole.planet.myplanet.databinding.AlertReferenceBinding
import org.ole.planet.myplanet.databinding.EditAttachementBinding
import org.ole.planet.myplanet.databinding.EditOtherInfoBinding
import org.ole.planet.myplanet.databinding.FragmentEditAchievementBinding
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding
import org.ole.planet.myplanet.databinding.RowlayoutBinding
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmAchievement.Companion.createReference
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.CheckboxListView
import org.ole.planet.myplanet.utilities.DialogUtils.getDialog
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.TimeUtils.getFormattedDate
import org.ole.planet.myplanet.utilities.Utilities

class EditAchievementFragment : BaseContainerFragment(), DatePickerDialog.OnDateSetListener {
    private lateinit var fragmentEditAchievementBinding: FragmentEditAchievementBinding
    private lateinit var editAttachmentBinding: EditAttachementBinding
    private lateinit var editOtherInfoBinding: EditOtherInfoBinding
    private lateinit var alertReferenceBinding: AlertReferenceBinding
    private lateinit var alertAddAttachmentBinding: AlertAddAttachmentBinding
    private lateinit var myLibraryAlertdialogBinding: MyLibraryAlertdialogBinding
    var user: RealmUserModel? = null
    private var achievement: RealmAchievement? = null
    private var referenceArray: JsonArray? = null
    private var achievementArray: JsonArray? = null
    private var resourceArray: JsonArray? = null
    private var referenceDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentEditAchievementBinding = FragmentEditAchievementBinding.inflate(inflater, container, false)
        user = profileDbHandler.userModel
        achievementArray = JsonArray()
        initializeData()
        setListeners()
        return fragmentEditAchievementBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            NavigationHelper.popBackStack(requireActivity().supportFragmentManager)
        }
    }
    
    private fun setListeners() {
        fragmentEditAchievementBinding.btnUpdate.setOnClickListener {
            val achievementId = user?.id + "@" + user?.planetCode
            val header = fragmentEditAchievementBinding.etAchievement.text.toString().trim { it <= ' ' }
            val goals = fragmentEditAchievementBinding.etGoals.text.toString().trim { it <= ' ' }
            val purpose = fragmentEditAchievementBinding.etPurpose.text.toString().trim { it <= ' ' }
            val sendToNation = fragmentEditAchievementBinding.cbSendToNation.isChecked.toString() + ""
            val achievementsJson = if (achievementArray != null) GsonUtils.gson.toJson(achievementArray) else "[]"
            val referencesJson = if (referenceArray != null) GsonUtils.gson.toJson(referenceArray) else "[]"

            fragmentEditAchievementBinding.btnUpdate.isEnabled = false
            Utilities.toast(activity, getString(R.string.saving))

            lifecycleScope.launch {
                databaseService.withRealmAsync { realm ->
                    realm.executeTransaction { transactionRealm ->
                        val achievement = transactionRealm.where(RealmAchievement::class.java)
                            .equalTo("_id", achievementId)
                            .findFirst()
                        if (achievement != null) {
                            achievement.achievementsHeader = header
                            achievement.goals = goals
                            achievement.purpose = purpose
                            achievement.sendToNation = sendToNation
                            achievement.setAchievements(GsonUtils.gson.fromJson(achievementsJson, JsonArray::class.java))
                            achievement.setReferences(GsonUtils.gson.fromJson(referencesJson, JsonArray::class.java))
                        }
                    }
                }
                Utilities.toast(activity, getString(R.string.achievement_saved))
                NavigationHelper.popBackStack(parentFragmentManager)
            }
        }
        fragmentEditAchievementBinding.btnCancel.setOnClickListener {
            NavigationHelper.popBackStack(parentFragmentManager)
        }
        fragmentEditAchievementBinding.btnAchievement.setOnClickListener {
            showAddAchievementAlert(null)
        }
        fragmentEditAchievementBinding.btnOther.setOnClickListener {
            showReferenceDialog(null)
        }
        fragmentEditAchievementBinding.txtDob.setOnClickListener {
            val now = Calendar.getInstance()
            val dpd = DatePickerDialog(requireActivity(), this, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH])
            dpd.datePicker.maxDate = Calendar.getInstance().timeInMillis
            dpd.show()
        }
    }

    private fun showAchievementAndInfo() {
        val config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single)
        fragmentEditAchievementBinding.llAttachment.removeAllViews()
        for (e in achievementArray!!) {
            editAttachmentBinding = EditAttachementBinding.inflate(LayoutInflater.from(activity))
            editAttachmentBinding.tvTitle.text = e.asJsonObject["title"].asString
            val flexboxLayout = editAttachmentBinding.flexbox
            flexboxLayout.removeAllViews()
            val chipCloud = ChipCloud(activity, flexboxLayout, config)
            for (element in e.asJsonObject.getAsJsonArray("resources")) {
                chipCloud.addChip(element.asJsonObject["title"].asString)
            }
            editAttachmentBinding.ivDelete.setOnClickListener {
                achievementArray?.remove(e)
                showAchievementAndInfo()
            }
            editAttachmentBinding.edit.setOnClickListener { showAddAchievementAlert(e.asJsonObject) }
            val editAttachmentView: View = editAttachmentBinding.root
            fragmentEditAchievementBinding.llAttachment.addView(editAttachmentView)
        }
    }

    private fun showReference() {
        fragmentEditAchievementBinding.llOtherInfo.removeAllViews()
        for (e in referenceArray!!) {
            editOtherInfoBinding = EditOtherInfoBinding.inflate(LayoutInflater.from(activity))
            editOtherInfoBinding.tvTitle.text = e.asJsonObject["name"].asString
            editOtherInfoBinding.ivDelete.setOnClickListener {
                referenceArray?.remove(e)
                showReference()
            }
            editOtherInfoBinding.edit.setOnClickListener { showReferenceDialog(e.asJsonObject) }
            val editOtherInfoView: View = editOtherInfoBinding.root
            fragmentEditAchievementBinding.llOtherInfo.addView(editOtherInfoView)
        }
    }

    private fun showReferenceDialog(`object`: JsonObject?) {
        alertReferenceBinding = AlertReferenceBinding.inflate(LayoutInflater.from(activity))
        val ar = arrayOf(
            alertReferenceBinding.etName,
            alertReferenceBinding.etPhone,
            alertReferenceBinding.etEmail,
            alertReferenceBinding.etRelationship
        )
        setPrevReference(ar, `object`)
        val alertReferenceView: View = alertReferenceBinding.root
        referenceDialog = getDialog(requireActivity(), getString(R.string.add_reference), alertReferenceView)
        referenceDialog?.show()
        referenceDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val name = alertReferenceBinding.etName.text.toString().trim { it <= ' ' }
            if (name.isEmpty()) {
                alertReferenceBinding.tlName.error = getString(R.string.name_is_required)
                return@setOnClickListener
            }
            if (`object` != null) referenceArray?.remove(`object`)
            if (referenceArray == null) referenceArray = JsonArray()
            referenceArray?.add(createReference(name, alertReferenceBinding.etRelationship, alertReferenceBinding.etPhone, alertReferenceBinding.etEmail))
            showReference()
            referenceDialog?.dismiss()
        }
    }

    private fun setPrevReference(ar: Array<EditText>, `object`: JsonObject?) {
        if (`object` != null) {
            ar[0].setText(`object`["name"].asString)
            ar[1].setText(`object`["phone"].asString)
            ar[2].setText(`object`["email"].asString)
            ar[3].setText(`object`["relationship"].asString)
        }
    }

    var date = ""
    private fun showAddAchievementAlert(`object`: JsonObject?) {
        alertAddAttachmentBinding = AlertAddAttachmentBinding.inflate(LayoutInflater.from(activity))
        alertAddAttachmentBinding.tvDate.setOnClickListener {
            val now = Calendar.getInstance()
            val dpd = DatePickerDialog(requireActivity(), { _: DatePicker?, i: Int, i1: Int, i2: Int ->
                date = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
                alertAddAttachmentBinding.tvDate.text = date },
                now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH])
            dpd.datePicker.maxDate = now.timeInMillis
            dpd.show()
        }
        resourceArray = JsonArray()
        val prevList = setUpOldAchievement(
            `object`,
            alertAddAttachmentBinding.etDesc,
            alertAddAttachmentBinding.etTitle,
            alertAddAttachmentBinding.tvDate as AppCompatTextView
        )
        alertAddAttachmentBinding.btnAddResources.setOnClickListener {
            showResourceListDialog(prevList)
        }
        val tintColor = ContextCompat.getColorStateList(requireContext(), R.color.daynight_textColor)
        alertAddAttachmentBinding.etDesc.backgroundTintList = tintColor
        alertAddAttachmentBinding.etTitle.backgroundTintList = tintColor
        val alertAddAttachmentView: View = alertAddAttachmentBinding.root
        AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setTitle(R.string.add_achievement)
            .setIcon(R.drawable.ic_edit)
            .setView(alertAddAttachmentView)
            .setCancelable(false)
            .setPositiveButton("Submit") { _: DialogInterface?, _: Int ->
                val desc = alertAddAttachmentBinding.etDesc.text.toString().trim { it <= ' ' }
                val title = alertAddAttachmentBinding.etTitle.text.toString().trim { it <= ' ' }
                if (title.isEmpty()) {
                    Toast.makeText(activity, getString(R.string.title_is_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (`object` != null) achievementArray?.remove(`object`)
                saveAchievement(desc, title)
            }.setNegativeButton(getString(R.string.cancel), null).show()
    }

    private fun setUpOldAchievement(`object`: JsonObject?, etDescription: EditText, etTitle: EditText, tvDate: AppCompatTextView): List<String?> {
        val prevList: MutableList<String?> = ArrayList()
        if (`object` != null) {
            etTitle.setText(`object`["title"].asString)
            etDescription.setText(`object`["description"].asString)
            tvDate.text = `object`["date"].asString
            val array = `object`.getAsJsonArray("resources")
            date = `object`["date"].asString
            for (o in array) {
                prevList.add(o.asJsonObject["title"].asString)
            }
            resourceArray = `object`.getAsJsonArray("resources")
        }
        return prevList
    }

    private fun saveAchievement(desc: String, title: String) {
        val `object` = JsonObject()
        `object`.addProperty("description", desc)
        `object`.addProperty("title", title)
        `object`.addProperty("date", date)
        `object`.add("resources", resourceArray)
        achievementArray?.add(`object`)
        showAchievementAndInfo()
    }

    private fun showResourceListDialog(prevList: List<String?>) {
        viewLifecycleOwner.lifecycleScope.launch {
            val list = databaseService.withRealm { realm ->
                val result = realm.where(RealmMyLibrary::class.java).findAll()
                realm.copyFromRealm(result)
            }

            withContext(Dispatchers.Main) {
                val builder = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
                builder.setTitle(R.string.select_resources)
                myLibraryAlertdialogBinding = MyLibraryAlertdialogBinding.inflate(LayoutInflater.from(activity))
                val myLibraryAlertdialogView: View = myLibraryAlertdialogBinding.root
                val lv = createResourceList(myLibraryAlertdialogBinding, list, prevList)
                builder.setView(myLibraryAlertdialogView)
                builder.setPositiveButton("Ok") { _: DialogInterface?, _: Int ->
                    val items = lv.selectedItemsList
                    resourceArray = JsonArray()
                    for (ii in items) {
                        resourceArray?.add(list[ii].serializeResource())
                    }
                }.setNegativeButton("Cancel", null).show()
            }
        }
    }

    override fun onDateSet(datePicker: DatePicker, i: Int, i1: Int, i2: Int) {
        fragmentEditAchievementBinding.txtDob.text =
            String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
    }

    private fun initializeData() {
        val achievementId = user?.id + "@" + user?.planetCode
        lifecycleScope.launch {
            achievement = databaseService.withRealm { realm ->
                val achievement = realm.where(RealmAchievement::class.java)
                    .equalTo("_id", achievementId)
                    .findFirst()
                if (achievement != null) realm.copyFromRealm(achievement) else null
            }

            if (achievement == null) {
                databaseService.withRealmAsync { realm ->
                    realm.executeTransaction { transactionRealm ->
                        val existing = transactionRealm.where(RealmAchievement::class.java)
                            .equalTo("_id", achievementId)
                            .findFirst()
                        if (existing == null) {
                            transactionRealm.createObject(
                                RealmAchievement::class.java,
                                achievementId
                            )
                        }
                    }
                }
                if (!isAdded) {
                    return@launch
                }
                achievement = databaseService.withRealm { realm ->
                    val achievement = realm.where(RealmAchievement::class.java)
                        .equalTo("_id", achievementId)
                        .findFirst()
                    if (achievement != null) realm.copyFromRealm(achievement) else null
                }
            }
            populateAchievementData()
        }
    }

    private fun populateAchievementData() {
        achievementArray = achievement?.achievementsArray ?: achievementArray
        referenceArray = achievement?.getReferencesArray() ?: referenceArray
        fragmentEditAchievementBinding.etAchievement.setText(achievement?.achievementsHeader)
        fragmentEditAchievementBinding.etPurpose.setText(achievement?.purpose)
        fragmentEditAchievementBinding.etGoals.setText(achievement?.goals)
        fragmentEditAchievementBinding.cbSendToNation.isChecked = achievement?.sendToNation.toBoolean()
        fragmentEditAchievementBinding.txtDob.text = if (TextUtils.isEmpty(user?.dob)) getString(R.string.birth_date) else getFormattedDate(user?.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        resourceArray = JsonArray()
        fragmentEditAchievementBinding.etFname.setText(user?.firstName)
        fragmentEditAchievementBinding.etMname.setText(user?.middleName)
        fragmentEditAchievementBinding.etLname.setText(user?.lastName)
        fragmentEditAchievementBinding.etBirthplace.setText(user?.birthPlace)
        if (achievementArray != null) {
            showAchievementAndInfo()
        }
        if (referenceArray != null) {
            showReference()
        }
    }

    private fun createResourceList(myLibraryAlertdialogBinding: MyLibraryAlertdialogBinding, list: List<RealmMyLibrary>, prevList: List<String?>): CheckboxListView {
        val names = ArrayList<String?>()
        val selected: ArrayList<Int> = ArrayList()
        for (i in list.indices) {
            names.add(list[i].title)
            if (prevList.contains(list[i].title)) selected.add(i)
        }
        val adapter: ArrayAdapter<String?> = object : ArrayAdapter<String?>(requireActivity(), R.layout.item_checkbox, R.id.checkBoxRowLayout, names) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val rowLayoutBinding = RowlayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                val textView = rowLayoutBinding.root
                textView.text = getItem(position)
                textView.isChecked = myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList.contains(position)
                myLibraryAlertdialogBinding.alertDialogListView.setItemChecked(position, myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList.contains(position))
                return textView
            }
        }
        myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList = selected
        myLibraryAlertdialogBinding.alertDialogListView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        myLibraryAlertdialogBinding.alertDialogListView.adapter = adapter
        return myLibraryAlertdialogBinding.alertDialogListView
    }

    override fun onDestroyView() {
        referenceDialog?.dismiss()
        referenceDialog = null
        super.onDestroyView()
    }

}
