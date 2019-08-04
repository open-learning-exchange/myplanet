package org.ole.planet.myplanet.ui.team;


import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamFragment extends Fragment {

    Realm mRealm;
    RecyclerView rvTeamList;
    EditText etSearch;

    public TeamFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_team, container, false);
        rvTeamList = v.findViewById(R.id.rv_team_list);
        etSearch = v.findViewById(R.id.et_search);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        v.findViewById(R.id.add_team).setOnClickListener(view -> {
            createTeamAlert();
        });
        return v;
    }

    private void createTeamAlert() {
        RealmUserModel user = new UserProfileDbHandler(getActivity()).getUserModel();
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_create_team, null);
        EditText etName = v.findViewById(R.id.et_name);
        EditText etDescription = v.findViewById(R.id.et_description);
        Spinner spnType = v.findViewById(R.id.spn_team_type);
        new AlertDialog.Builder(getActivity()).setTitle("Enter Team Detail")
                .setView(v)
                .setPositiveButton("Save", (dialogInterface, i) -> {
                    String name = etName.getText().toString();
                    String desc = etDescription.getText().toString();
                    String type = spnType.getSelectedItemPosition() == 0 ? "local" : "sync";
                    if (name.isEmpty()) {
                        Utilities.toast(getActivity(), "Name is required");
                    } else if (type.isEmpty()) {
                        Utilities.toast(getActivity(), "Type is required");
                    } else {
                        RealmMyTeam.createTeam(name, desc, type, mRealm, user);
                        Utilities.toast(getActivity(), "Team Created");
                        setTeamList();

                    }
                }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvTeamList.setLayoutManager(new LinearLayoutManager(getActivity()));
        setTeamList();
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                List<RealmMyTeam> list = mRealm.where(RealmMyTeam.class).isEmpty("teamId").contains("name", charSequence.toString(), Case.INSENSITIVE).findAll();
                AdapterTeamList adapterTeamList = new AdapterTeamList(getActivity(), list, mRealm);
                rvTeamList.setAdapter(adapterTeamList);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void setTeamList() {
        List<RealmMyTeam> list = mRealm.where(RealmMyTeam.class).isEmpty("teamId").findAll();
        AdapterTeamList adapterTeamList = new AdapterTeamList(getActivity(), list, mRealm);
        rvTeamList.setAdapter(adapterTeamList);
    }
}
