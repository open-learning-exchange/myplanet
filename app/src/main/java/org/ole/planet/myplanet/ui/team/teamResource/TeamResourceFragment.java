package org.ole.planet.myplanet.ui.team.teamResource;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.TeamPageListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.CheckboxListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamResourceFragment extends BaseTeamFragment implements TeamPageListener {

    AdapterTeamResource adapterLibrary;
    RecyclerView rvResource;
    TextView tvNodata;

    public TeamResourceFragment() {
    }




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_team_resource, container, false);
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvResource = getView().findViewById(R.id.rv_resource);
        tvNodata = getView().findViewById(R.id.tv_nodata);
        showLibraryList();
//        if (MainApplication.showDownload)
//            showResourceListDialog();
        getView().findViewById(R.id.fab_add_resource).setOnClickListener(view -> showResourceListDialog());
    }




    private void showLibraryList() {
        List<RealmMyLibrary> libraries = mRealm.where(RealmMyLibrary.class).in("id", RealmMyTeam.getResourceIds(teamId, mRealm).toArray(new String[0])).findAll();
        adapterLibrary = new AdapterTeamResource(getActivity(), libraries, mRealm, teamId, settings);
        rvResource.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        rvResource.setAdapter(adapterLibrary);
        showNoData(tvNodata, adapterLibrary.getItemCount());
    }

    private void showResourceListDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View convertView = inflater.inflate(R.layout.my_library_alertdialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        CheckboxListView lv = convertView.findViewById(R.id.alertDialog_listView);
        alertDialogBuilder.setTitle("Select Resource : ");
        List<RealmMyLibrary> libraries = mRealm.where(RealmMyLibrary.class).not().in("_id", RealmMyTeam.getResourceIds(teamId, mRealm).toArray(new String[0])).findAll();
        alertDialogBuilder.setView(convertView).setPositiveButton("Add", (dialogInterface, i) -> {
            ArrayList<Integer> selected = lv.getSelectedItemsList();
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            for (Integer se : selected) {
                RealmMyTeam team = mRealm.createObject(RealmMyTeam.class, UUID.randomUUID().toString());
                team.setTeamId(teamId);
                team.setTitle(libraries.get(se).getTitle());
                team.setSourcePlanet(user.getParentCode());
                team.setResourceId(libraries.get(se).get_id());
                team.setDocType("resourceLink");
                team.setUpdated(true);
                team.setTeamType("local");
                team.setTeamPlanetCode(user.getPlanetCode());
            }
            mRealm.commitTransaction();
            showLibraryList();
        }).setNegativeButton("Cancel", null);
        AlertDialog alertDialog = alertDialogBuilder.create();
        listSetting(alertDialog, libraries, lv);
    }

    private void listSetting(AlertDialog alertDialog, List<RealmMyLibrary> libraries, CheckboxListView lv) {
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < libraries.size(); i++) {
            names.add(libraries.get(i).getTitle());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, names);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setCheckChangeListener(() -> {
            (alertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(lv.getSelectedItemsList().size() > 0);
        });
        lv.setAdapter(adapter);
        alertDialog.show();
        (alertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(lv.getSelectedItemsList().size() > 0);
    }


    @Override
    public void onAddDocument() {
        showResourceListDialog();
    }
}
