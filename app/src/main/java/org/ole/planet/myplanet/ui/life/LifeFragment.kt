package org.ole.planet.myplanet.ui.life

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnStartDragListener
import org.ole.planet.myplanet.databinding.FragmentLifeBinding
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.utils.ItemReorderHelper
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class LifeFragment : BaseRecyclerFragment<RealmMyLife?>(), OnStartDragListener {
    private lateinit var lifeAdapter: LifeAdapter
    private var itemTouchHelper: ItemTouchHelper? = null
    private val viewModel: LifeViewModel by viewModels()
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

    private fun initAdapter() {
        if (!::lifeAdapter.isInitialized) {
            lifeAdapter = LifeAdapter(requireContext(), this,
                visibilityCallback = { myLife, isVisible ->
                    viewModel.updateVisibility(myLife, isVisible)
                },
                reorderCallback = { list ->
                    viewModel.updateMyLifeListOrder(list)
                }
            )
        }
    }

    override suspend fun getAdapter(): RecyclerView.Adapter<*> {
        initAdapter()
        return lifeAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initAdapter()
        super.onViewCreated(view, savedInstanceState)
        viewModel.getAllMyLife()
        recyclerView.setHasFixedSize(true)
        setupUI(binding.myLifeParentLayout, requireActivity())
        val callback: ItemTouchHelper.Callback = ItemReorderHelper(lifeAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, RecyclerView.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.myLifeList.collect { myLifeList ->
                        lifeAdapter.submitList(myLifeList)
                    }
                }
                launch {
                    viewModel.message.collect { message ->
                        Utilities.toast(requireContext(), message)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        viewHolder?.let { itemTouchHelper?.startDrag(it) }
    }
}
