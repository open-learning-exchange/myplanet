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
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.realm.RealmList
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddResourceBinding
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.service.UserProfileHandler
import org.ole.planet.myplanet.ui.components.CheckboxListView
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.LocaleUtils
import org.ole.planet.myplanet.utilities.Utilities.toast

@AndroidEntryPoint
class AddResourceActivity : AppCompatActivity() {
    @Inject
    lateinit var userProfileDbHandler: UserProfileHandler
    @Inject
    lateinit var resourcesRepository: ResourcesRepository
    private lateinit var binding: ActivityAddResourceBinding
    var userModel: RealmUserModel? = null
    var subjects: RealmList<String>? = null
    var levels: RealmList<String>? = null
    private var resourceFor: RealmList<String>? = null
    private var resourceUrl: String? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding = ActivityAddResourceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        userModel = userProfileDbHandler.userModel
        resourceUrl = intent.getStringExtra("resource_local_url")
        levels = RealmList()
        subjects = RealmList()
        resourceFor = RealmList()
        initializeViews()
    }

    private fun initializeViews() {
        val etYear = findViewById<EditText>(R.id.et_year)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        etYear.setText(currentYear.toString())
        binding.fileUrl.text = getString(R.string.file, resourceUrl)
        binding.tvAddedBy.text = userModel?.name
        binding.tvLevels.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_levels), levels, view,getString(R.string.levels))
        }
        binding.tvSubject.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_subjects), subjects, view,getString(R.string.subject))
        }
        binding.tvResourceFor.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_resource_for), resourceFor, view,getString(R.string.resource_for))
        }
        binding.btnSubmit.setOnClickListener { saveResource() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun saveResource() {
        val title = binding.etTitle.text.toString().trim { it <= ' ' }
        if (!validate(title)) return
        val id = UUID.randomUUID().toString()
        val resource = RealmMyLibrary().apply {
            this.id = id
            this.title = title
            createResource(this, id)
            setUserId(userModel?.id)
        }
        lifecycleScope.launch {
            resourcesRepository.saveLibraryItem(resource)
            resourcesRepository.markResourceAdded(userModel?.id, id)
            toast(this@AddResourceActivity, getString(R.string.added_to_my_library))
            finish()
        }
    }

    private fun createResource(resource: RealmMyLibrary, id: String) {
        resource.addedBy = binding.tvAddedBy.text.toString().trim { it <= ' ' }
        resource.author = binding.etAuthor.text.toString().trim { it <= ' ' }
        resource.resourceId = id
        resource.year = binding.etYear.text.toString().trim { it <= ' ' }
        resource.description = binding.etDescription.text.toString().trim { it <= ' ' }
        resource.publisher = binding.etPublisher.text.toString().trim { it <= ' ' }
        resource.linkToLicense = binding.etLinkToLicense.text.toString().trim { it <= ' ' }
        resource.openWith = binding.spnOpenWith.selectedItem.toString()
        resource.language = binding.spnLang.selectedItem.toString()
        resource.mediaType = binding.spnMedia.selectedItem.toString()
        resource.resourceType = binding.spnResourceType.selectedItem.toString()
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
            binding.tlTitle.error = getString(R.string.title_is_required)
            return false
        }
        val description = binding.etDescription.text.toString().trim()
        if (description.isEmpty()) {
            binding.etDescription.error = getString(R.string.description_is_required)
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
                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (currentFragment is ResourceDetailFragment) {
                    finish()
                }else {
                    showExitConfirmationDialog()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
