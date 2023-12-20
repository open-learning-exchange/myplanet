package org.ole.planet.myplanet.ui.myPersonals;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal;
import org.ole.planet.myplanet.databinding.FragmentMyPersonalsBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UploadManager;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class MyPersonalsFragment extends Fragment implements OnSelectedMyPersonal {
    private FragmentMyPersonalsBinding fragmentMyPersonalsBinding;
    Realm mRealm;
    ProgressDialog pg;

    public MyPersonalsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentMyPersonalsBinding = FragmentMyPersonalsBinding.inflate(inflater, container, false);

        pg = new ProgressDialog(getActivity());
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        fragmentMyPersonalsBinding.rvMypersonal.setLayoutManager(new LinearLayoutManager(getActivity()));
        fragmentMyPersonalsBinding.addMyPersonal.setOnClickListener(vi -> {
            AddResourceFragment f = new AddResourceFragment();
            Bundle b = new Bundle();
            b.putInt("type", 1);
            f.setArguments(b);
            f.show(getChildFragmentManager(), getString(R.string.add_resource));
        });
        return fragmentMyPersonalsBinding.getRoot();

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setAdapter();
    }

    private void setAdapter() {
        RealmUserModel model = new UserProfileDbHandler(getActivity()).getUserModel();
        List<RealmMyPersonal> realmMyPersonals = mRealm.where(RealmMyPersonal.class).equalTo("userId", model.id).findAll();
        AdapterMyPersonal personalAdapter = new AdapterMyPersonal(getActivity(), realmMyPersonals);
        personalAdapter.setListener(this);
        personalAdapter.setRealm(mRealm);
        fragmentMyPersonalsBinding.rvMypersonal.setAdapter(personalAdapter);
        showNodata();
        mRealm.addChangeListener(realm -> showNodata());
    }

    private void showNodata() {
        Utilities.log("Show nodata");
        if (fragmentMyPersonalsBinding.rvMypersonal.getAdapter().getItemCount() == 0) {
            fragmentMyPersonalsBinding.tvNodata.setVisibility(View.VISIBLE);
            fragmentMyPersonalsBinding.tvNodata.setText(R.string.no_data_available_please_click_button_to_add_new_resource_in_mypersonal);
        } else {
            fragmentMyPersonalsBinding.tvNodata.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed()) mRealm.close();
    }

    @Override
    public void onUpload(RealmMyPersonal personal) {
        pg.setMessage("Please wait......");
        pg.show();
        UploadManager.getInstance().uploadMyPersonal(personal, s -> {
            Utilities.toast(getActivity(), s);
            pg.dismiss();
        });
    }

    @Override
    public void onAddedResource() {
        showNodata();
    }
}
