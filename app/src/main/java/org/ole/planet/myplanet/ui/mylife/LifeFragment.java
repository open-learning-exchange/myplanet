package org.ole.planet.myplanet.ui.mylife;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import java.util.List;


public class LifeFragment extends BaseRecyclerFragment<RealmMyLife> {

    AdapterMyLife adapterMyLife;

    public LifeFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_life;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        List <RealmMyLife> myLifeList = RealmMyLife.getMyLifeByUserId(mRealm,model.getId());
        adapterMyLife = new AdapterMyLife(getContext(),myLifeList,mRealm);
        return adapterMyLife;
    }
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        KeyboardUtils.setupUI(getView().findViewById(R.id.my_life_parent_layout),getActivity());
    }

}
