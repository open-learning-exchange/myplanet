package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import io.realm.RealmList
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddResourceBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.createFromResource
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.onAdd
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.CheckboxListView
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.Utilities.toast
import java.util.Calendar
import java.util.UUID

class AddResourceActivity : AppCompatActivity() {
    private lateinit var activityAddResourceBinding: ActivityAddResourceBinding
    private lateinit var mRealm: Realm
    var userModel: RealmUserModel? = null
    var subjects: RealmList<String>? = null
    var levels: RealmList<String>? = null
    private var resourceFor: RealmList<String>? = null
    private var resourceUrl: String? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activityAddResourceBinding = ActivityAddResourceBinding.inflate(layoutInflater)
        setContentView(activityAddResourceBinding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        userModel = UserProfileDbHandler(this).userModel
        resourceUrl = intent.getStringExtra("resource_local_url")
        levels = RealmList()
        subjects = RealmList()
        resourceFor = RealmList()
        mRealm = DatabaseService(this).realmInstance
        initializeViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
    }

    private fun initializeViews() {
        val etYear = findViewById<EditText>(R.id.et_year)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        etYear.setText(currentYear.toString())
        activityAddResourceBinding.fileUrl.text = getString(R.string.file, resourceUrl)
        activityAddResourceBinding.tvAddedBy.text = userModel?.name
        activityAddResourceBinding.tvLevels.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_levels), levels, view,getString(R.string.levels))
        }
        activityAddResourceBinding.tvSubject.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_subjects), subjects, view,getString(R.string.subject))
        }
        activityAddResourceBinding.tvResourceFor.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_resource_for), resourceFor, view,getString(R.string.resource_for))
        }
        activityAddResourceBinding.btnSubmit.setOnClickListener { saveResource() }
        activityAddResourceBinding.btnCancel.setOnClickListener { finish() }
    }

    private fun saveResource() {
        val title = activityAddResourceBinding.etTitle.text.toString().trim { it <= ' ' }
        if (!validate(title)) return
        val id = UUID.randomUUID().toString()
        mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            val resource = realm.createObject(RealmMyLibrary::class.java, id)
            resource.title = title
            createResource(resource, id)
        }, Realm.Transaction.OnSuccess {
            val myObject = mRealm.where(RealmMyLibrary::class.java)
                .equalTo("resourceId", id).findFirst()
            createFromResource(myObject, mRealm, userModel?.id)
            onAdd(mRealm, "resources", userModel?.id, id)
            toast(this@AddResourceActivity, getString(R.string.added_to_my_library))
            navigateToResourceDetail(myObject?.resourceId)
        })
    }

    private fun navigateToResourceDetail(libraryId: String?) {
        val existingFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ResourceDetailFragment
        if (existingFragment == null) {
            val fragment = ResourceDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("libraryId", libraryId)
                }
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commitAllowingStateLoss()
        } else {
            existingFragment.arguments = Bundle().apply {
                putString("libraryId", libraryId)
            }
        }
    }

    private fun createResource(resource: RealmMyLibrary, id: String) {
        resource.addedBy = activityAddResourceBinding.tvAddedBy.text.toString().trim { it <= ' ' }
        resource.author = activityAddResourceBinding.etAuthor.text.toString().trim { it <= ' ' }
        resource.resourceId = id
        resource.year = activityAddResourceBinding.etYear.text.toString().trim { it <= ' ' }
        resource.description = activityAddResourceBinding.etDescription.text.toString().trim { it <= ' ' }
        resource.publisher = activityAddResourceBinding.etPublisher.text.toString().trim { it <= ' ' }
        resource.linkToLicense = activityAddResourceBinding.etLinkToLicense.text.toString().trim { it <= ' ' }
        resource.openWith = activityAddResourceBinding.spnOpenWith.selectedItem.toString()
        resource.language = activityAddResourceBinding.spnLang.selectedItem.toString()
        resource.mediaType = activityAddResourceBinding.spnMedia.selectedItem.toString()
        resource.resourceType = activityAddResourceBinding.spnResourceType.selectedItem.toString()
        resource.subject = subjects
        resource.setUserId(RealmList())
        resource.level = levels
        resource.createdDate = Calendar.getInstance().timeInMillis
        resource.resourceFor = resourceFor
        resource.resourceLocalAddress = resourceUrl
        resource.resourceOffline = true
        resource.filename = resourceUrl?.let { it.substring(it.lastIndexOf("/")) }
    }

    private fun validate(title: String): Boolean {
        if (title.isEmpty()) {
            activityAddResourceBinding.tlTitle.error = getString(R.string.title_is_required)
            return false
        }
        if (levels?.isEmpty() == true) {
            toast(this, getString(R.string.level_is_required))
            return false
        }
        if (subjects?.isEmpty() == true) {
            toast(this, getString(R.string.subject_is_required))
            return false
        }
        return true
    }
    private fun showMultiSelectList(list: Array<String>, items: MutableList<String>?, view: View, title: String) {
        val listView = CheckboxListView(this)
        val adapter = ArrayAdapter(this, R.layout.rowlayout, R.id.checkBoxRowLayout, list)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        listView.adapter = adapter

        items?.forEach { selectedItem ->
            val index = list.indexOf(selectedItem)
            if (index >= 0) {
                listView.setItemChecked(index, true)
            }
        }

        AlertDialog.Builder(this, R.style.AlertDialogTheme).setView(listView).setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
            val selected = listView.checkedItemPositions
            items?.clear()
            var selection = ""
            for (i in 0 until listView.count) {
                if (selected[i]) {
                    val s = list[i]
                    selection += "$s, "
                    items?.add(s)
                }
            }
            if (selection.isEmpty()) {
                (view as TextView).text = title
            } else {
                (view as TextView).text = selection.trimEnd(',', ' ')
            }
        }.setNegativeButton(R.string.dismiss, null).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                showExitConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this,R.style.AlertDialogTheme)
            .setMessage(R.string.are_you_sure_you_want_to_exit_your_data_will_be_lost)
            .setPositiveButton(R.string.yes_i_want_to_exit) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }
}
