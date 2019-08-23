package org.ole.planet.myplanet.ui.team;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import org.ole.planet.myplanet.base.BaseNewsFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

public abstract class BaseTeamFragment extends BaseNewsFragment {

  public  DatabaseService dbService;
  public   RealmUserModel user;
  public   String teamId;
  public   RealmMyTeam team;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            teamId = getParentFragment().getArguments().getString("id", "");
        }
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        user = mRealm.copyFromRealm(new UserProfileDbHandler(getActivity()).getUserModel());
        Utilities.log("Team id " + teamId);
        team = mRealm.where(RealmMyTeam.class).equalTo("_id", teamId).findFirst();
    }

    @Override
    public void setData(List<RealmNews> list) {

    }
}
