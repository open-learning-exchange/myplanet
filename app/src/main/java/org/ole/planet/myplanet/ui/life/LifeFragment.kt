package org.ole.planet.myplanet.ui.life

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnStartDragListener
import org.ole.planet.myplanet.databinding.FragmentLifeBinding
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.utils.ItemReorderHelper
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class LifeFragment : BaseRecyclerFragment<RealmMyLife?>(), OnStartDragListener {
    private lateinit var lifeAdapter: LifeAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    @Inject
    lateinit var lifeRepository: LifeRepository
    private var _binding: FragmentLifeBinding? = null
    private val binding get() = checkNotNull(_binding)
    override fun getLayout(): Int = R.layout.fragment_life

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        if (view != null) {
            _binding = FragmentLifeBinding.bind(view)
        }
        return view
    }

    override suspend fun getAdapter(): RecyclerView.Adapter<out RecyclerView.ViewHolder> {
        lifeAdapter = LifeAdapter(requireContext(), this,
            visibilityCallback = { myLife, isVisible ->
                myLife._id?.let { id ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            lifeRepository.updateVisibility(isVisible, id)
                        }
                        if (!isVisible) {
                            Utilities.toast(requireContext(), myLife.title + context?.getString(R.string.is_now_hidden))
                        } else {
                            Utilities.toast(requireContext(), myLife.title + " " + context?.getString(R.string.is_now_shown))
                        }
                        refreshList()
                    }
                }
            },
            reorderCallback = { list ->
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        lifeRepository.updateMyLifeListOrder(list)
                    }
                }
            }
        )
        val callback: ItemTouchHelper.Callback = ItemReorderHelper(lifeAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
        return lifeAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshList()
        recyclerView.setHasFixedSize(true)
        setupUI(binding.myLifeParentLayout, requireActivity())
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, RecyclerView.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    private fun refreshList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = sharedPrefManager.getUserId().ifEmpty { "--" }
            var myLifeList = lifeRepository.getMyLifeByUserId(userId)
            if (myLifeList.isEmpty()) {
                lifeRepository.seedMyLifeIfEmpty(userId, getMyLifeListBase(userId))
                myLifeList = lifeRepository.getMyLifeByUserId(userId)
            }
            if (::lifeAdapter.isInitialized) {
                lifeAdapter.submitList(myLifeList)
            }
        }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("my_achievement", userId, getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId, getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_calendar", userId, getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId, getString(R.string.mypersonals)))
        return myLifeList
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        viewHolder?.let { itemTouchHelper?.startDrag(it) }
    }
}
