package org.ole.planet.myplanet.ui.mylife;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener;
import org.ole.planet.myplanet.ui.mylife.helper.SimpleItemTouchHelperCallback;
import org.ole.planet.myplanet.utilities.KeyboardUtils;

import java.util.List;


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
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), RecyclerView.VERTICAL);
        recyclerView.addItemDecoration(dividerItemDecoration);
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        mItemTouchHelper.startDrag(viewHolder);
    }


}
