package org.ole.planet.myplanet.ui.life

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.callback.OnStartDragListener
import org.ole.planet.myplanet.databinding.FragmentLifeBinding
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.utils.ItemReorderHelper
import org.ole.planet.myplanet.utils.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class LifeFragment : BaseRecyclerFragment<MyLife?>(), OnStartDragListener {
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

    override suspend fun getAdapter(): ListAdapter<*, *> = initAdapter()

    private fun initAdapter(): LifeAdapter {
        if (!::lifeAdapter.isInitialized) {
            lifeAdapter = LifeAdapter(
                requireContext(),
                this,
                visibilityCallback = { myLife, isVisible ->
                    val id = myLife._id.takeIf { it.isNotBlank() }
                        ?: myLife.imageId?.takeIf { it.isNotBlank() }
                        ?: myLife.title
                    if (!id.isNullOrEmpty()) {
                        viewModel.updateVisibility(isVisible, id)
                        if (!isVisible) {
                            Utilities.toast(requireContext(), myLife.title + context?.getString(R.string.is_now_hidden))
                        } else {
                            Utilities.toast(requireContext(), myLife.title + " " + context?.getString(R.string.is_now_shown))
                        }
                    }
                },
                reorderCallback = { list ->
                    viewModel.updateMyLifeListOrder(list)
                }
            )
            val callback: ItemTouchHelper.Callback = ItemReorderHelper(lifeAdapter)
            itemTouchHelper = ItemTouchHelper(callback)
        }
        itemTouchHelper?.attachToRecyclerView(recyclerView)
        return lifeAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.setHasFixedSize(true)
        setupUI(binding.myLifeParentLayout, requireActivity())
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, RecyclerView.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)

        initAdapter()
        collectWhenStarted(viewModel.myLifeList) { list ->
            lifeAdapter.submitList(list)
        }
        viewModel.loadMyLifeList()
    }

    override fun onDestroyView() {
        itemTouchHelper?.attachToRecyclerView(null)
        _binding = null
        super.onDestroyView()
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        viewHolder?.let { itemTouchHelper?.startDrag(it) }
    }
}
