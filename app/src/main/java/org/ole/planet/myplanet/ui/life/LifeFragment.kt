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
import org.ole.planet.myplanet.model.RealmMyLife.Companion.getMyLifeByUserId
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.utilities.ItemReorderHelper
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class LifeFragment : BaseRecyclerFragment<RealmMyLife?>(), OnStartDragListener {
    private lateinit var lifeAdapter: LifeAdapter
    private var mItemTouchHelper: ItemTouchHelper? = null
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

    override fun getAdapter(): RecyclerView.Adapter<*> {
        lifeAdapter = LifeAdapter(requireContext(), this,
            visibilityCallback = { myLife, isVisible ->
                myLife._id?.let { id ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        lifeRepository.updateVisibility(isVisible, id)
                        withContext(Dispatchers.Main) {
                            if (!isVisible) {
                                Utilities.toast(requireContext(), myLife.title + context?.getString(R.string.is_now_hidden))
                            } else {
                                Utilities.toast(requireContext(), myLife.title + " " + context?.getString(R.string.is_now_shown))
                            }
                            refreshList()
                        }
                    }
                }
            },
            reorderCallback = { list ->
                lifecycleScope.launch(Dispatchers.IO) {
                    lifeRepository.updateMyLifeListOrder(list)
                }
            }
        )
        return lifeAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshList()
        recyclerView.setHasFixedSize(true)
        setupUI(binding.myLifeParentLayout, requireActivity())
        val callback: ItemTouchHelper.Callback = ItemReorderHelper(lifeAdapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(recyclerView)
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, RecyclerView.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    private fun refreshList() {
        val myLifeList = getMyLifeByUserId(mRealm, model?.id)
        lifeAdapter.submitList(mRealm.copyFromRealm(myLifeList))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        viewHolder?.let { mItemTouchHelper?.startDrag(it) }
    }
}
