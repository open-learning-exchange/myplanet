package org.ole.planet.myplanet.ui.enterprises;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class FinanceFragment extends Fragment {

    RecyclerView rvFinance;
    FloatingActionButton fab;
    TextView nodata;
    Realm mRealm;
    public FinanceFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v=  inflater.inflate(R.layout.fragment_finance, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        rvFinance = v.findViewById(R.id.rv_finance);
        fab = v.findViewById(R.id.add_transaction);
        nodata = v.findViewById(R.id.tv_nodata);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }
}
