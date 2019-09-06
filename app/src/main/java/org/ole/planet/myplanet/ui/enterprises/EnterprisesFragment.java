package org.ole.planet.myplanet.ui.enterprises;


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
import org.ole.planet.myplanet.ui.team.AdapterTeamList;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Case;
import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class EnterprisesFragment extends Fragment {


    Realm mRealm;
    RecyclerView rvEnterpriseList;
    EditText etSearch;

    public EnterprisesFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_enterprises, container, false);
        rvEnterpriseList = v.findViewById(R.id.rv_enterprise_list);
        etSearch = v.findViewById(R.id.et_search);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        v.findViewById(R.id.add_enterprise).setOnClickListener(view -> {
            createEnterpriseAlert();
        });
        return v;
    }

    private void createEnterpriseAlert() {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_create_team, null);
        EditText etName = v.findViewById(R.id.et_name);
        EditText etDescription = v.findViewById(R.id.et_description);
        Spinner spnType = v.findViewById(R.id.spn_team_type);
        new AlertDialog.Builder(getActivity()).setTitle("Enter Enterprise Detail")
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
                        //Todo Create team and set list
                        Utilities.toast(getActivity(), "Hoax Enterprise Created");
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
        rvEnterpriseList.setLayoutManager(new LinearLayoutManager(getActivity()));
        setEnterpriseList();
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void setEnterpriseList() {
    }

}
