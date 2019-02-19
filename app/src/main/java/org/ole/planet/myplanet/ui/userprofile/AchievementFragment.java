package org.ole.planet.myplanet.ui.userprofile;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class AchievementFragment extends Fragment {

    TextView tvGoal, tvAchievement, tvPurpose, tvName, tvFirstName;
    RecyclerView rvOther;
    Realm mRealm;
    LinearLayout llAchievement, llData;
    RealmUserModel user;
    public AchievementFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_achievement, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        rvOther = v.findViewById(R.id.rv_other_info);
        tvGoal = v.findViewById(R.id.tv_goals);
        tvName = v.findViewById(R.id.tv_name);
        tvFirstName = v.findViewById(R.id.tv_first_name);
      //  llData = v.findViewById(R.id.ll_data);
        tvPurpose = v.findViewById(R.id.tv_purpose);
        llAchievement = v.findViewById(R.id.ll_achievement);
        tvAchievement = v.findViewById(R.id.tv_achievement_header);
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RealmAchievement achievement = mRealm.where(RealmAchievement.class).equalTo("_id", user.getId()).findFirst();
        tvFirstName.setText(user.getFirstName());
        tvName.setText(String.format("%s %s %s", user.getFirstName(), user.getMiddleName(), user.getLastName()));
        if (achievement!=null){
            tvGoal.setText(achievement.getGoals());
            tvPurpose.setText(achievement.getPurpose());
            tvAchievement.setText(achievement.getAchievementsHeader());
            llAchievement.removeAllViews();
            TextView v = (TextView) LayoutInflater.from(getActivity()).inflate(android.R.layout.simple_list_item_1, null);
            for (String s: achievement.getAchievements()
                 ) {
                v.setText(s);
                llAchievement.addView(v);
            }
            rvOther.setLayoutManager(new LinearLayoutManager(getActivity()));
            rvOther.setAdapter(new AdapterOtherInfo(getActivity(), achievement.getOtherInfo()));
        }else{
         //   llData.setVisibility(View.GONE);
        }
    }
}
