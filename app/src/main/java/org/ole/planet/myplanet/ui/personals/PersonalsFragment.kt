package org.ole.planet.myplanet.ui.personals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentMyPersonalsBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class PersonalsFragment : Fragment(), OnSelectedMyPersonal {
    private var _binding: FragmentMyPersonalsBinding? = null
    private val binding get() = _binding!!
    private lateinit var pg: DialogUtils.CustomProgressDialog
    private var addResourceFragment: AddResourceFragment? = null
    private var personalAdapter: PersonalsAdapter? = null

    @Inject
    lateinit var uploadManager: UploadManager
    @Inject
    lateinit var personalsRepository: PersonalsRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyPersonalsBinding.inflate(inflater, container, false)
        pg = DialogUtils.getCustomProgressDialog(requireContext())
        binding.rvMypersonal.layoutManager = LinearLayoutManager(activity)
        binding.addMyPersonal.setOnClickListener {
            addResourceFragment = AddResourceFragment()
            val b = Bundle()
            b.putInt("type", 1)
            addResourceFragment?.arguments = b
            addResourceFragment?.show(childFragmentManager, getString(R.string.add_resource))
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter()
    }

    private fun setAdapter() {
        val model = userProfileDbHandler.userModel
        personalAdapter = PersonalsAdapter(requireActivity())
        personalAdapter?.setListener(this)
        binding.rvMypersonal.adapter = personalAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            personalsRepository.getPersonalResources(model?.id).collectLatest { realmMyPersonals ->
                personalAdapter?.submitList(realmMyPersonals)
                showNodata()
            }
        }
        showNodata()
    }

    private fun showNodata() {
        if (binding.rvMypersonal.adapter?.itemCount == 0) {
            binding.tvNodata.visibility = View.VISIBLE
            binding.tvNodata.setText(R.string.no_data_available_please_click_button_to_add_new_resource_in_mypersonal)
        } else {
            binding.tvNodata.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onUpload(personal: RealmMyPersonal?) {
        pg.setText("Please wait...")
        pg.show()
        if (personal != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val result = uploadManager.uploadMyPersonal(personal)
                    Utilities.toast(activity, result)
                } catch (e: Exception) {
                    Utilities.toast(activity, "Upload failed: ${e.message}")
                } finally {
                    pg.dismiss()
                }
            }
        }
    }

    override fun onAddedResource() {
        // List updates are handled via repository flow
    }

    override fun onEditPersonal(personal: RealmMyPersonal) {
        val alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(requireContext()))
        alertMyPersonalBinding.etDescription.setText(personal.description)
        alertMyPersonalBinding.etTitle.setText(personal.title)
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.edit_personal)
            .setIcon(R.drawable.ic_edit)
            .setView(alertMyPersonalBinding.root)
            .setPositiveButton(R.string.button_submit) { _, _ ->
                val title = alertMyPersonalBinding.etTitle.text.toString().trim { it <= ' ' }
                val desc = alertMyPersonalBinding.etDescription.text.toString().trim { it <= ' ' }
                if (title.isEmpty()) {
                    Utilities.toast(requireContext(), getString(R.string.please_enter_title))
                    return@setPositiveButton
                }
                val id = personal.id ?: personal._id
                if (id != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        personalsRepository.updatePersonalResource(id) { realmPersonal ->
                            realmPersonal.description = desc
                            realmPersonal.title = title
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDeletePersonal(personal: RealmMyPersonal) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setMessage(R.string.delete_record)
            .setPositiveButton(R.string.ok) { _, _ ->
                val id = personal.id ?: personal._id
                if (id != null) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        personalsRepository.deletePersonalResource(id)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
