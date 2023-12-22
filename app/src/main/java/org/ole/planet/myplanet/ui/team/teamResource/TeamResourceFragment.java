package org.ole.planet.myplanet.ui.team.teamResource;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.TeamPageListener;
import org.ole.planet.myplanet.databinding.FragmentTeamResourceBinding;
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.CheckboxListView;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TeamResourceFragment extends BaseTeamFragment implements TeamPageListener {
    private FragmentTeamResourceBinding fragmentTeamResourceBinding;
    AdapterTeamResource adapterLibrary;

    public TeamResourceFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentTeamResourceBinding = FragmentTeamResourceBinding.inflate(inflater, container, false);
        return fragmentTeamResourceBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showLibraryList();
        fragmentTeamResourceBinding.fabAddResource.setOnClickListener(view -> showResourceListDialog());
    }

    private void showLibraryList() {
        List<RealmMyLibrary> libraries = mRealm.where(RealmMyLibrary.class).in("id", RealmMyTeam.getResourceIds(teamId, mRealm).toArray(new String[0])).findAll();
        adapterLibrary = new AdapterTeamResource(getActivity(), libraries, mRealm, teamId, settings);
        fragmentTeamResourceBinding.rvResource.setLayoutManager(new GridLayoutManager(getActivity(), 3));
        fragmentTeamResourceBinding.rvResource.setAdapter(adapterLibrary);
        showNoData(fragmentTeamResourceBinding.tvNodata, adapterLibrary.getItemCount());
    }

    private void showResourceListDialog() {
        MyLibraryAlertdialogBinding myLibraryAlertdialogBinding = MyLibraryAlertdialogBinding.inflate(getLayoutInflater());

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());

        alertDialogBuilder.setTitle(R.string.select_resource);
        List<RealmMyLibrary> libraries = mRealm.where(RealmMyLibrary.class).not().in("_id", RealmMyTeam.getResourceIds(teamId, mRealm).toArray(new String[0])).findAll();
        alertDialogBuilder.setView(myLibraryAlertdialogBinding.getRoot()).setPositiveButton(R.string.add, (dialogInterface, i) -> {
            ArrayList<Integer> selected = myLibraryAlertdialogBinding.alertDialogListView.getSelectedItemsList();
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
            for (Integer se : selected) {
                RealmMyTeam team = mRealm.createObject(RealmMyTeam.class, UUID.randomUUID().toString());
                team.teamId = teamId;
                team.title = libraries.get(se).title;
                team.status = user.parentCode;
                team.resourceId = libraries.get(se).get_id();
                team.docType = "resourceLink";
                team.updated = true;
                team.teamType = "local";
                team.teamPlanetCode = user.planetCode;
            }
            mRealm.commitTransaction();
            showLibraryList();
        }).setNegativeButton(R.string.cancel, null);

        AlertDialog alertDialog = alertDialogBuilder.create();

        listSetting(alertDialog, libraries, myLibraryAlertdialogBinding.alertDialogListView);
    }

    private void listSetting(AlertDialog alertDialog, List<RealmMyLibrary> libraries, CheckboxListView lv) {
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < libraries.size(); i++) {
            names.add(libraries.get(i).title);
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
