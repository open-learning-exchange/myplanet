package org.ole.planet.myplanet.ui.myPersonals

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentMyPersonalsBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.repository.MyPersonalsRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class MyPersonalsFragment : Fragment(), OnSelectedMyPersonal {
    private lateinit var fragmentMyPersonalsBinding: FragmentMyPersonalsBinding
    private lateinit var pg: DialogUtils.CustomProgressDialog
    private var addResourceFragment: AddResourceFragment? = null
    
    @Inject
    lateinit var uploadManager: UploadManager
    @Inject
    lateinit var myPersonalsRepository: MyPersonalsRepository
    fun refreshFragment() {
        if (isAdded) {
            setAdapter()
            if (addResourceFragment != null && addResourceFragment?.isAdded == true) {
                addResourceFragment?.dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMyPersonalsBinding = FragmentMyPersonalsBinding.inflate(inflater, container, false)
        pg = DialogUtils.getCustomProgressDialog(requireContext())
        fragmentMyPersonalsBinding.rvMypersonal.layoutManager = LinearLayoutManager(activity)
        fragmentMyPersonalsBinding.addMyPersonal.setOnClickListener {
            addResourceFragment = AddResourceFragment()
            val b = Bundle()
            b.putInt("type", 1)
            addResourceFragment?.arguments = b
            addResourceFragment?.setMyPersonalsFragment(this)
            addResourceFragment?.show(childFragmentManager, getString(R.string.add_resource))
        }
        return fragmentMyPersonalsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter()
    }

    private fun setAdapter() {
        val model = UserProfileDbHandler(requireContext()).userModel
        val realmMyPersonals = myPersonalsRepository.getMyPersonals(model?.id.toString())
        val personalAdapter = AdapterMyPersonal(requireActivity(), realmMyPersonals)
        personalAdapter.setListener(this)
        fragmentMyPersonalsBinding.rvMypersonal.adapter = personalAdapter
        showNodata()
    }

    private fun showNodata() {
        if (fragmentMyPersonalsBinding.rvMypersonal.adapter?.itemCount == 0) {
            fragmentMyPersonalsBinding.tvNodata.visibility = View.VISIBLE
            fragmentMyPersonalsBinding.tvNodata.setText(R.string.no_data_available_please_click_button_to_add_new_resource_in_mypersonal)
        } else {
            fragmentMyPersonalsBinding.tvNodata.visibility = View.GONE
        }
    }

    override fun onUpload(personal: RealmMyPersonal?) {
        pg.setText("Please wait......")
        pg.show()
        if (personal != null) {
            uploadManager.uploadMyPersonal(personal) { s: String? ->
                if (s != null) {
                    Utilities.toast(activity, s)
                }
                pg.dismiss()
            }
        }
    }

    override fun onAddedResource() {
        showNodata()
    }

    override fun onEdit(personal: RealmMyPersonal) {
        val alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context))
        alertMyPersonalBinding.etDescription.setText(personal.description)
        alertMyPersonalBinding.etTitle.setText(personal.title)
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.edit_personal)
            .setIcon(R.drawable.ic_edit)
            .setView(alertMyPersonalBinding.root)
            .setPositiveButton(R.string.button_submit) { _: DialogInterface?, _: Int ->
                val title = alertMyPersonalBinding.etDescription.text.toString().trim { it <= ' ' }
                val desc = alertMyPersonalBinding.etTitle.text.toString().trim { it <= ' ' }
                if (title.isEmpty()) {
                    Utilities.toast(requireContext(), R.string.please_enter_title.toString())
                    return@setPositiveButton
                }
                personal.description = desc
                personal.title = title
                myPersonalsRepository.updatePersonal(personal)
                setAdapter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDelete(personal: RealmMyPersonal) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setMessage(R.string.delete_record)
            .setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                myPersonalsRepository.deletePersonal(personal._id!!)
                setAdapter()
            }.setNegativeButton(R.string.cancel, null).show()
    }
}
