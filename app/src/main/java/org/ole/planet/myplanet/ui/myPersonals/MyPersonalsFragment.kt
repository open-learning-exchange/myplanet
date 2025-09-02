package org.ole.planet.myplanet.ui.myPersonals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal
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
    lateinit var mRealm: Realm
    private lateinit var pg: DialogUtils.CustomProgressDialog
    private var addResourceFragment: AddResourceFragment? = null
    private val viewModel: MyPersonalsViewModel by viewModels()

    @Inject lateinit var uploadManager: UploadManager
    @Inject lateinit var databaseService: DatabaseService
    @Inject lateinit var userProfileDbHandler: UserProfileDbHandler

    fun refreshFragment() {
        if (isAdded) {
            viewModel.loadPersonalItems(userProfileDbHandler.userModel?.id)
            if (addResourceFragment != null && addResourceFragment?.isAdded == true) {
                addResourceFragment?.dismiss()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentMyPersonalsBinding =
            FragmentMyPersonalsBinding.inflate(inflater, container, false)
        pg = DialogUtils.getCustomProgressDialog(requireContext())
        mRealm = databaseService.realmInstance
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
        viewModel.loadPersonalItems(userProfileDbHandler.userModel?.id)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.personalItems.collect { items ->
                    setAdapter(items)
                }
            }
        }
    }

    private fun setAdapter(items: List<RealmMyPersonal>) {
        val personalAdapter = AdapterMyPersonal(requireActivity(), items)
        personalAdapter.setListener(this)
        personalAdapter.setRealm(mRealm)
        fragmentMyPersonalsBinding.rvMypersonal.adapter = personalAdapter
        showNodata(items)
    }

    private fun showNodata(items: List<RealmMyPersonal>) {
        if (items.isEmpty()) {
            fragmentMyPersonalsBinding.tvNodata.visibility = View.VISIBLE
            fragmentMyPersonalsBinding.tvNodata.setText(
                R.string.no_data_available_please_click_button_to_add_new_resource_in_mypersonal
            )
        } else {
            fragmentMyPersonalsBinding.tvNodata.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        if (::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        super.onDestroy()
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
        viewModel.loadPersonalItems(userProfileDbHandler.userModel?.id)
    }
}
