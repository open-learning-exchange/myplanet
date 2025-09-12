package org.ole.planet.myplanet.ui.myPersonals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
import org.ole.planet.myplanet.databinding.FragmentMyPersonalsBinding
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.resources.AddResourceFragment
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.Utilities
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.MyPersonalRepository

@AndroidEntryPoint
class MyPersonalsFragment : Fragment(), OnSelectedMyPersonal {
    private var _binding: FragmentMyPersonalsBinding? = null
    private val binding get() = _binding!!
    private lateinit var pg: DialogUtils.CustomProgressDialog
    private var addResourceFragment: AddResourceFragment? = null
    private var personalAdapter: AdapterMyPersonal? = null
    
    @Inject
    lateinit var uploadManager: UploadManager
    @Inject
    lateinit var myPersonalRepository: MyPersonalRepository
    fun refreshFragment() {
        if (isAdded) {
            setAdapter()
            if (addResourceFragment != null && addResourceFragment?.isAdded == true) {
                addResourceFragment?.dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyPersonalsBinding.inflate(inflater, container, false)
        pg = DialogUtils.getCustomProgressDialog(requireContext())
        binding.rvMypersonal.layoutManager = LinearLayoutManager(activity)
        binding.addMyPersonal.setOnClickListener {
            addResourceFragment = AddResourceFragment()
            val b = Bundle()
            b.putInt("type", 1)
            addResourceFragment?.arguments = b
            addResourceFragment?.setMyPersonalsFragment(this)
            addResourceFragment?.show(childFragmentManager, getString(R.string.add_resource))
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setAdapter()
    }

    private fun setAdapter() {
        val model = UserProfileDbHandler(requireContext()).userModel
        personalAdapter = AdapterMyPersonal(requireActivity(), mutableListOf())
        personalAdapter?.setListener(this)
        personalAdapter?.onDelete = { personal ->
            viewLifecycleOwner.lifecycleScope.launch {
                personal.id?.let { myPersonalRepository.deletePersonalResource(it) }
            }
        }
        personalAdapter?.onEdit = { personal, title, desc ->
            viewLifecycleOwner.lifecycleScope.launch {
                personal.id?.let { myPersonalRepository.updatePersonalResource(it, title, desc) }
            }
        }
        binding.rvMypersonal.adapter = personalAdapter
        viewLifecycleOwner.lifecycleScope.launch {
            myPersonalRepository.getPersonalResources(model?.id).collectLatest { realmMyPersonals ->
                personalAdapter?.updateList(realmMyPersonals)
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
        // List updates are handled via repository flow
    }
}
