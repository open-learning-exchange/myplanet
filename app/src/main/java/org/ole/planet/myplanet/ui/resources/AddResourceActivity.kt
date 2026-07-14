package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import io.realm.RealmList
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddResourceBinding
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.LocalResourceRequest
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.ui.components.CheckboxAdapter
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.LocaleUtils
import org.ole.planet.myplanet.utils.Utilities.toast

@AndroidEntryPoint
class AddResourceActivity : AppCompatActivity() {
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var resourcesRepository: ResourcesRepository
    @Inject
    lateinit var teamsRepository: TeamsRepository
    private lateinit var binding: ActivityAddResourceBinding
    var userModel: RealmUser? = null
    var subjects: RealmList<String>? = null
    var levels: RealmList<String>? = null
    private var resourceFor: RealmList<String>? = null
    private var resourceUrl: String? = null
    private var teamId: String? = null

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
        resourceUrl = intent.getStringExtra("resource_local_url")
        teamId = intent.getStringExtra("teamId")
        levels = RealmList()
        subjects = RealmList()
        resourceFor = RealmList()
        val resourceId = intent.getStringExtra("resource_id")
        val isEditMode = intent.getBooleanExtra("is_edit_mode", false)
        initializeViews()
        if (isEditMode && resourceId != null) {
            supportActionBar?.title = getString(R.string.edit_resource)
            binding.btnSubmit.text = getString(R.string.save_changes)
            lifecycleScope.launch {
                prefillFields(resourceId)
            }
        }
        setupPrivateResourceCheckbox()
        lifecycleScope.launch {
            userModel = userSessionManager.getUserModel()
            binding.tvAddedBy.text = userModel?.name
        }
    }

    private fun setupPrivateResourceCheckbox() {
        binding.cbPrivateResource.isVisible = teamId != null
        binding.cbPrivateResource.isChecked = teamId != null
    }

    private fun initializeViews() {
        val etYear = findViewById<EditText>(R.id.et_year)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        etYear.setText(currentYear.toString())
        binding.fileUrl.text = getString(R.string.file, resourceUrl)
        binding.tvLevels.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_levels), levels, view,getString(R.string.levels))
        }
        binding.tvSubject.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_subjects), subjects, view,getString(R.string.subject))
        }
        binding.tvResourceFor.setOnClickListener { view: View ->
            showMultiSelectList(resources.getStringArray(R.array.array_resource_for), resourceFor, view,getString(R.string.resource_for))
        }
        setupHintSpinner(binding.spnLang, getString(R.string.language), resources.getStringArray(R.array.language))
        setupHintSpinner(binding.spnOpenWith, getString(R.string.select_open_with), resources.getStringArray(R.array.open_With))
        setupHintSpinner(binding.spnMedia, getString(R.string.select_media), resources.getStringArray(R.array.media))
        setupHintSpinner(binding.spnResourceType, getString(R.string.select_resource_type), resources.getStringArray(R.array.resource_type))
        binding.etTitle.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tlTitle.error = null
            } else {
                val title = binding.etTitle.text.toString().trim()
                val isEditMode = intent.getBooleanExtra("is_edit_mode", false)
                if (title.isNotEmpty() && !isEditMode) {
                    lifecycleScope.launch {
                        if (resourcesRepository.resourceTitleExists(title)) {
                            binding.tlTitle.error = getString(R.string.resource_title_already_exists)
                        }
                    }
                }
            }
        }
        binding.btnSubmit.setOnClickListener { saveResource() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun setupHintSpinner(spinner: Spinner, hint: String, entries: Array<String>) {
        val items = listOf(hint) + entries.toList()
        val hintColor = ContextCompat.getColor(this, R.color.hint_color)
        val textColor = ContextCompat.getColor(this, R.color.daynight_textColor)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun isEnabled(position: Int) = position != 0
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(if (position == 0) hintColor else textColor)
                return view
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.isSingleLine = false
                view.maxLines = 2
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(if (position == 0) hintColor else textColor)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private suspend fun prefillFields(resourceId: String) {
        val resource = resourcesRepository.getResourceById(resourceId) ?: return
        binding.etTitle.setText(resource.title)
        binding.etAuthor.setText(resource.author)
        binding.etYear.setText(resource.year)
        binding.etDescription.setText(resource.description)
        binding.etPublisher.setText(resource.publisher)
        binding.etLinkToLicense.setText(resource.linkToLicense)

        resource.subject?.forEach { subjects?.add(it) }
        resource.level?.forEach { levels?.add(it) }

        binding.tvSubject.text = resource.subject?.joinToString(", ")
            ?.takeIf { it.isNotEmpty() } ?: getString(R.string.subject)
        binding.tvLevels.text = resource.level?.joinToString(", ")
            ?.takeIf { it.isNotEmpty() } ?: getString(R.string.levels)
    }

    private fun saveResource() {
        val title = binding.etTitle.text.toString().trim { it <= ' ' }
        if (!validate(title)) return
        val isEditMode = intent.getBooleanExtra("is_edit_mode", false)
        val resourceId = intent.getStringExtra("resource_id")
        binding.btnSubmit.isEnabled = false

        if (isEditMode && resourceId != null) {
            lifecycleScope.launch {
                val result = resourcesRepository.updateLocalResource(
                    resourceId = resourceId,
                    title = title,
                    author = binding.etAuthor.text.toString().trim(),
                    year = binding.etYear.text.toString().trim(),
                    description = binding.etDescription.text.toString().trim(),
                    publisher = binding.etPublisher.text.toString().trim(),
                    linkToLicense = binding.etLinkToLicense.text.toString().trim(),
                    subjects = subjects,
                    levels = levels
                )
                if (result.isSuccess) {
                    toast(this@AddResourceActivity, getString(R.string.resource_updated))
                    finish()
                } else {
                    toast(this@AddResourceActivity, getString(R.string.failed_to_update_resource))
                    binding.btnSubmit.isEnabled = true
                }
            }
            return
        }

        val isPrivateTeamResource = binding.cbPrivateResource.isChecked && teamId != null
        val request = LocalResourceRequest(
            title = title,
            addedBy = binding.tvAddedBy.text.toString().trim { it <= ' ' },
            author = binding.etAuthor.text.toString().trim { it <= ' ' },
            year = binding.etYear.text.toString().trim { it <= ' ' },
            description = binding.etDescription.text.toString().trim { it <= ' ' },
            publisher = binding.etPublisher.text.toString().trim { it <= ' ' },
            linkToLicense = binding.etLinkToLicense.text.toString().trim { it <= ' ' },
            openWith = if (binding.spnOpenWith.selectedItemPosition > 0) binding.spnOpenWith.selectedItem.toString() else "",
            language = if (binding.spnLang.selectedItemPosition > 0) binding.spnLang.selectedItem.toString() else "",
            mediaType = if (binding.spnMedia.selectedItemPosition > 0) binding.spnMedia.selectedItem.toString() else "",
            resourceType = if (binding.spnResourceType.selectedItemPosition > 0) binding.spnResourceType.selectedItem.toString() else "",
            subjects = subjects,
            levels = levels,
            resourceFor = resourceFor,
            resourceUrl = resourceUrl,
            userId = userModel?.id,
            isPrivateTeamResource = isPrivateTeamResource,
            teamId = teamId
        )
        lifecycleScope.launch {
            val result = resourcesRepository.saveLocalResource(request)
            if (result.isSuccess) {
                val message = if (isPrivateTeamResource) {
                    getString(R.string.resource_added_to_team)
                } else {
                    getString(R.string.added_to_my_library)
                }
                toast(this@AddResourceActivity, message)
                finish()
            } else {
                binding.tlTitle.error = getString(R.string.resource_title_already_exists)
                binding.btnSubmit.isEnabled = true
            }
        }
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
        val listView = RecyclerView(this)
        listView.layoutManager = LinearLayoutManager(this)

        val initialSelectedIndices = mutableListOf<Int>()
        items?.forEach { selectedItem ->
            val index = list.indexOf(selectedItem)
            if (index >= 0) {
                initialSelectedIndices.add(index)
            }
        }

        val adapter = CheckboxAdapter(initialSelectedIndices)
        adapter.submitList(list.toList())
        listView.adapter = adapter

        AlertDialog.Builder(this, R.style.AlertDialogTheme).setView(listView).setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
            val selected = adapter.selectedItemsList
            items?.clear()
            val selectionList = mutableListOf<String>()
            for (i in selected) {
                val s = list[i]
                selectionList.add(s)
                items?.add(s)
            }
            val selection = selectionList.joinToString(", ")
            if (selection.isEmpty()) {
                (view as TextView).text = title
            } else {
                (view as TextView).text = selection
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
