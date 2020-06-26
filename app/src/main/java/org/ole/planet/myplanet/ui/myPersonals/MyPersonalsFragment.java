package org.ole.planet.myplanet.ui.myPersonals;


import android.app.ProgressDialog;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UploadManager;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyPersonalsFragment extends Fragment implements OnSelectedMyPersonal {

    RecyclerView rvMyPersonal;
    TextView tvNodata;
    Realm mRealm;
    ProgressDialog pg;
    public MyPersonalsFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_my_personals, container, false);
        pg = new ProgressDialog(getActivity());
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        rvMyPersonal = v.findViewById(R.id.rv_mypersonal);
        tvNodata = v.findViewById(R.id.tv_nodata);
        rvMyPersonal.setLayoutManager(new LinearLayoutManager(getActivity()));
        v.findViewById(R.id.add_my_personal).setOnClickListener(vi -> {
            AddResourceFragment f = new AddResourceFragment();
            Bundle b = new Bundle();
            b.putInt("type", 1);
            f.setArguments(b);
            f.show(getChildFragmentManager(), "Add Resource");
        });
        return v;
    }

//    @Override
//    public void onResume() {
//        super.onResume();
//        if (rvMyPersonal != null)
//            setAdapter();
//    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setAdapter();
    }

    private void setAdapter() {
        RealmUserModel model = new UserProfileDbHandler(getActivity()).getUserModel();
        List<RealmMyPersonal> realmMyPersonals = mRealm.where(RealmMyPersonal.class).equalTo("userId", model.getId()).findAll();
        AdapterMyPersonal personalAdapter = new AdapterMyPersonal(getActivity(), realmMyPersonals);
        personalAdapter.setListener(this);
        personalAdapter.setRealm(mRealm);
        rvMyPersonal.setAdapter(personalAdapter);
        showNodata();
        mRealm.addChangeListener(realm -> showNodata());
    }

    private void showNodata() {
        Utilities.log("Show nodata");
        if (rvMyPersonal.getAdapter().getItemCount() == 0){
            tvNodata.setVisibility(View.VISIBLE);
            tvNodata.setText("No data available, please click + button to add new resource in myPersonal.");
        }else{
            tvNodata.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }
    @Override
    public void onUpload(RealmMyPersonal personal) {
        pg.setMessage("Please wait......");
        pg.show();
        UploadManager.getInstance().uploadMyPersonal(personal, s -> {
            Utilities.toast(getActivity(),s);
            pg.dismiss();
        });
    }

    @Override
    public void onAddedResource() {
        showNodata();
    }
}
