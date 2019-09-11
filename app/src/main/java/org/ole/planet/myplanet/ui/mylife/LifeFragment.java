package org.ole.planet.myplanet.ui.mylife;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener;
import org.ole.planet.myplanet.ui.mylife.helper.SimpleItemTouchHelperCallback;
import org.ole.planet.myplanet.utilities.KeyboardUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LifeFragment extends BaseRecyclerFragment<RealmMyLife> implements OnStartDragListener {

    AdapterMyLife adapterMyLife;
    private ItemTouchHelper mItemTouchHelper;

    public LifeFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_life;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        List<RealmMyLife> myLifeList = RealmMyLife.getMyLifeByUserId(mRealm, model.getId());
        adapterMyLife = new AdapterMyLife(getContext(), myLifeList, mRealm, this);
        return adapterMyLife;
    }



    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        recyclerView.setHasFixedSize(true);
        KeyboardUtils.setupUI(getView().findViewById(R.id.my_life_parent_layout), getActivity());
        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(adapterMyLife);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(recyclerView);

    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        mItemTouchHelper.startDrag(viewHolder);
    }







}
