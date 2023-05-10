package org.ole.planet.myplanet.ui.team;

import static android.content.Context.MODE_PRIVATE;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.ole.planet.myplanet.base.BaseNewsFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.sync.SyncActivity;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

public abstract class BaseTeamFragment extends BaseNewsFragment {
    public static SharedPreferences settings;
    public DatabaseService dbService;
    public RealmUserModel user;
    public String teamId;
    public RealmMyTeam team;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getParentFragment().getArguments() != null) {
            teamId = getParentFragment().getArguments().getString("id", "");
        } else if (getArguments() != null) {
            teamId = getArguments().getString("id", "");
        }
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        if (new UserProfileDbHandler(getActivity()).getUserModel() != null)
            user = mRealm.copyFromRealm(new UserProfileDbHandler(getActivity()).getUserModel());
        Utilities.log("Team id " + teamId);
        team = mRealm.where(RealmMyTeam.class).equalTo("_id", teamId).findFirst();
        settings = getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    public void setData(List<RealmNews> list) {
    }
}
