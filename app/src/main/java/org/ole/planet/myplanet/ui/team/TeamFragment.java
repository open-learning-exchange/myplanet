package org.ole.planet.myplanet.ui.team;


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
import org.ole.planet.myplanet.ui.team.AdapterTeamList;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmQuery;

/**
 * A simple {@link Fragment} subclass.
 */

public class TeamFragment extends Fragment {


    Realm mRealm;
    RecyclerView rvTeamList;
    EditText etSearch;
    String type;

    public TeamFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = getArguments().getString("type");
            Utilities.log("Team fragment");
        }
        Utilities.log("Team fragment");

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
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_create_team, null);
        EditText etName = v.findViewById(R.id.et_name);
        EditText etDescription = v.findViewById(R.id.et_description);
        Spinner spnType = v.findViewById(R.id.spn_team_type);
        if (type != null) {
            spnType.setVisibility(View.GONE);
        }
        new AlertDialog.Builder(getActivity()).setTitle(String.format("Enter %s Detail", type == null ? "Team" : "Enterprise"))
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
                        createTeam(name, desc, type);
                        Utilities.toast(getActivity(), "Team Created");
                        setTeamList();

                    }
                }).setNegativeButton("Cancel", null).show();
    }

    public void createTeam(String name, String desc, String type) {
        RealmUserModel user = new UserProfileDbHandler(getActivity()).getUserModel();
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmMyTeam team = mRealm.createObject(RealmMyTeam.class, UUID.randomUUID().toString());
        team.setStatus("active");
        team.setCreatedDate(new Date().getTime());
        if (type != null)
            team.setTeamType(type);
        team.setName(name);
        team.setDescription(desc);
        team.setTeamId("");
        team.setType(this.type == null? "team" : "enterprise");
        team.setUser_id(user.getId());
        team.setParentCode(user.getParentCode());
        team.setTeamPlanetCode(user.getPlanetCode());
        mRealm.commitTransaction();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }

    @Override
    public void onResume() {
        super.onResume();
        setTeamList();

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
                RealmQuery<RealmMyTeam> query = mRealm.where(RealmMyTeam.class).isEmpty("teamId").notEqualTo("type", "enterprise").notEqualTo("status", "archived").contains("name", charSequence.toString(), Case.INSENSITIVE);
                AdapterTeamList adapterTeamList = new AdapterTeamList(getActivity(), getList(query), mRealm);
                rvTeamList.setAdapter(adapterTeamList);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private List<RealmMyTeam> getList(RealmQuery<RealmMyTeam> query) {
        if (type == null) {
            query = query.notEqualTo("type", "enterprise");
        } else {
            query = query.equalTo("type", "enterprise");
        }
        return query.findAll();
    }

    private void setTeamList() {
        RealmQuery<RealmMyTeam> query = mRealm.where(RealmMyTeam.class).isEmpty("teamId").notEqualTo("status", "archived");

        AdapterTeamList adapterTeamList = new AdapterTeamList(getActivity(), getList(query), mRealm);
        adapterTeamList.setType(type);
        getView().findViewById(R.id.type).setVisibility(type == null ? View.VISIBLE : View.GONE);
        rvTeamList.setAdapter(adapterTeamList);
    }
}

