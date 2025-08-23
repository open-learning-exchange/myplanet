package org.ole.planet.myplanet.ui.myPersonals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding
import org.ole.planet.myplanet.databinding.FragmentMyPersonalsBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class MyPersonalsFragment : Fragment(), OnSelectedMyPersonal {
    private lateinit var fragmentMyPersonalsBinding: FragmentMyPersonalsBinding
    private lateinit var personalAdapter: AdapterMyPersonal
    lateinit var mRealm: Realm
    private lateinit var pg: DialogUtils.CustomProgressDialog
    private var addResourceFragment: AddResourceFragment? = null

    @Inject
    lateinit var uploadManager: UploadManager
    @Inject
    lateinit var databaseService: DatabaseService
    fun refreshFragment() {
        if (isAdded) {
            addResourceFragment?.dismiss()
            loadDataAndSetAdapter()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentMyPersonalsBinding = FragmentMyPersonalsBinding.inflate(inflater, container, false)
        pg = DialogUtils.getCustomProgressDialog(requireContext())
        mRealm = databaseService.realmInstance
        setupRecyclerView()
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
        loadDataAndSetAdapter()
        mRealm.addChangeListener { loadDataAndSetAdapter() }
    }

    private fun setupRecyclerView() {
        personalAdapter = AdapterMyPersonal(requireActivity())
        personalAdapter.setListener(this)
        fragmentMyPersonalsBinding.rvMypersonal.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = personalAdapter
        }
    }

    private fun loadDataAndSetAdapter() {
        val user = UserProfileDbHandler(requireContext()).userModel
        val myPersonals = mRealm.where(RealmMyPersonal::class.java).equalTo("userId", user?.id).findAll()
        personalAdapter.submitList(myPersonals)
        showNodata(myPersonals.isEmpty())
    }

    private fun showNodata(isListEmpty: Boolean) {
        if (isListEmpty) {
            fragmentMyPersonalsBinding.tvNodata.visibility = View.VISIBLE
            fragmentMyPersonalsBinding.tvNodata.setText(R.string.no_data_available_please_click_button_to_add_new_resource_in_mypersonal)
        } else {
            fragmentMyPersonalsBinding.tvNodata.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        if (::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.removeAllChangeListeners()
        }
        super.onDestroy()
    }

    override fun onUpload(personal: RealmMyPersonal) {
        pg.setText("Please wait......")
        pg.show()
        uploadManager.uploadMyPersonal(personal) { s: String? ->
            s?.let { Utilities.toast(activity, it) }
            pg.dismiss()
        }
    }

    override fun onAddedResource() {
        loadDataAndSetAdapter()
    }

    override fun onEdit(personal: RealmMyPersonal) {
        val alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(requireContext()))
        alertMyPersonalBinding.etDescription.setText(personal.description)
        alertMyPersonalBinding.etTitle.setText(personal.title)
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setTitle(R.string.edit_personal)
            .setIcon(R.drawable.ic_edit)
            .setView(alertMyPersonalBinding.root)
            .setPositiveButton(R.string.button_submit) { _: DialogInterface?, _: Int ->
                val title = alertMyPersonalBinding.etTitle.text.toString().trim()
                val desc = alertMyPersonalBinding.etDescription.text.toString().trim()
                if (title.isEmpty()) {
                    Utilities.toast(requireContext(), getString(R.string.please_enter_title))
                    return@setPositiveButton
                }
                mRealm.executeTransaction {
                    personal.description = desc
                    personal.title = title
                }
                onAddedResource()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDelete(personal: RealmMyPersonal) {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            .setMessage(R.string.delete_record)
            .setPositiveButton(R.string.ok) { _, _ ->
                mRealm.executeTransaction {
                    personal.deleteFromRealm()
                }
                onAddedResource()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
