package org.ole.planet.myplanet.base;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;

import java.util.List;

public abstract class BaseMemberFragment extends BaseTeamFragment {
    public abstract List<RealmUserModel> getList();
    public abstract RecyclerView.Adapter getAdapter();
    public abstract RecyclerView.LayoutManager getLayoutManager();
    public RecyclerView rvMember;
    public TextView tvNodata;




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_members, container, false);
        rvMember = v.findViewById(R.id.rv_member);
        tvNodata = v.findViewById(R.id.tv_nodata);
        return v;
    }




    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvMember.setLayoutManager(getLayoutManager());
        rvMember.setAdapter(getAdapter());
        showNoData(tvNodata, getList().size());
    }
}
