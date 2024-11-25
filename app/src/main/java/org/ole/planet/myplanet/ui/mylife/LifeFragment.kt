package org.ole.planet.myplanet.ui.mylife

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener
import org.ole.planet.myplanet.ui.mylife.helper.SimpleItemTouchHelperCallback
import org.ole.planet.myplanet.utilities.KeyboardUtils.setupUI

class LifeFragment : BaseRecyclerFragment<RealmMyLife?>(), OnStartDragListener {
    private lateinit var adapterMyLife: AdapterMyLife
    private var mItemTouchHelper: ItemTouchHelper? = null
    override fun getLayout(): Int {
        return R.layout.fragment_life
    }

    override fun getAdapter(): RecyclerView.Adapter<*> {
        val myLifeList = RealmMyLife.getMyLifeByUserId(mRealm, model?.id)
        adapterMyLife = AdapterMyLife(requireContext(), myLifeList, mRealm, this)
        return adapterMyLife
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.setHasFixedSize(true)
        setupUI(view.findViewById(R.id.my_life_parent_layout), requireActivity())
        val callback: ItemTouchHelper.Callback = SimpleItemTouchHelperCallback(adapterMyLife)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(recyclerView)
        val dividerItemDecoration = DividerItemDecoration(recyclerView.context, RecyclerView.VERTICAL)
        recyclerView.addItemDecoration(dividerItemDecoration)
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
        viewHolder?.let { mItemTouchHelper?.startDrag(it) }
    }
}
