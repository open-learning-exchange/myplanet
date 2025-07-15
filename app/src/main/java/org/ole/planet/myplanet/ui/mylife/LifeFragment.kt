package org.ole.planet.myplanet.ui.mylife

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyLife.Companion.getMyLifeByUserId
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener
import org.ole.planet.myplanet.ui.mylife.helper.SimpleItemTouchHelperCallback
import org.ole.planet.myplanet.databinding.FragmentLifeBinding
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI

class LifeFragment : BaseRecyclerFragment<RealmMyLife?>(), OnStartDragListener {
    private lateinit var adapterMyLife: AdapterMyLife
    private var mItemTouchHelper: ItemTouchHelper? = null
    private var _binding: FragmentLifeBinding? = null
    private val binding get() = _binding!!
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
        val myLifeList = getMyLifeByUserId(mRealm, model?.id)
        adapterMyLife = AdapterMyLife(requireContext(), myLifeList, mRealm, this)
        return adapterMyLife
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.setHasFixedSize(true)
        setupUI(binding.myLifeParentLayout, requireActivity())
        val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(adapterMyLife)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(recyclerView)
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, RecyclerView.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        viewHolder?.let { mItemTouchHelper?.startDrag(it) }
    }
}
