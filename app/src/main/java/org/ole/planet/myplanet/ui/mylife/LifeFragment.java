package org.ole.planet.myplanet.ui.mylife;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener;
import org.ole.planet.myplanet.ui.mylife.helper.SimpleItemTouchHelperCallback;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import java.util.List;


public class LifeFragment extends BaseRecyclerFragment<RealmMyLife> implements OnStartDragListener {

    // TODO: Rename and change types of parameters

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
        List <RealmMyLife> myLifeList = RealmMyLife.getMyLifeByUserId(mRealm,model.getId());
        adapterMyLife = new AdapterMyLife(getContext(),myLifeList,mRealm,this);
        return adapterMyLife;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        recyclerView.setHasFixedSize(true);
        KeyboardUtils.setupUI(getView().findViewById(R.id.my_life_parent_layout),getActivity());
        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(adapterMyLife);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        mItemTouchHelper.startDrag(viewHolder);
    }
}
