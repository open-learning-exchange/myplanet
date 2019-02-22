package org.ole.planet.myplanet.ui.userprofile;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.base.BaseResourceFragment;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class AchievementFragment extends BaseContainerFragment {

    TextView tvGoal, tvAchievement, tvPurpose, tvName, tvFirstName;
    RecyclerView rvOther;
    Realm mRealm;
    LinearLayout llAchievement, llData;
    RealmUserModel user;
    OnHomeItemClickListener listener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener)
            listener = (OnHomeItemClickListener) context;
    }

    public AchievementFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_achievement, container, false);
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
        v.findViewById(R.id.btn_edit).setOnClickListener(vi -> {
            if (listener != null)
                listener.openCallFragment(new EditAchievementFragment());
        });
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RealmAchievement achievement = mRealm.where(RealmAchievement.class).equalTo("_id", user.getId() + "@" + user.getPlanetCode()).findFirst();
        Utilities.log("User id " + user.getId());
        tvFirstName.setText(user.getFirstName());
        tvName.setText(String.format("%s %s %s", user.getFirstName(), user.getMiddleName(), user.getLastName()));
        if (achievement != null) {
            tvGoal.setText(achievement.getGoals());
            tvPurpose.setText(achievement.getPurpose());
            tvAchievement.setText(achievement.getAchievementsHeader());
            llAchievement.removeAllViews();
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.row_achievement, null);
            for (String s : achievement.getAchievements()
            ) {
                createView(v,s);
            }
            rvOther.setLayoutManager(new LinearLayoutManager(getActivity()));
            rvOther.setAdapter(new AdapterOtherInfo(getActivity(), achievement.getOtherInfo()));
        } else {
            //   llData.setVisibility(View.GONE);
        }
    }

    private void createView(View v, String s) {
        TextView tv = v.findViewById(R.id.tv_title);
        Button btn = v.findViewById(R.id.btn_attachment);
        llAchievement.removeAllViews();
        JsonElement ob = new Gson().fromJson(s, JsonElement.class);
        if (ob instanceof JsonObject) {
            tv.setText(JsonUtils.getString("description", ob.getAsJsonObject()));
            btn.setVisibility(View.VISIBLE);
            ArrayList<RealmMyLibrary> libraries = getList(((JsonObject) ob).getAsJsonArray("resources"));
            btn.setOnClickListener(view -> {
                if (libraries.isEmpty()) {
                    showDownloadDialog(libraries);
                } else {
                    showResourceList(libraries);
                }
            });
        } else {
            btn.setVisibility(View.GONE);
            tv.setText(ob.getAsString());
        }
        llAchievement.addView(v);
    }

    private ArrayList<RealmMyLibrary> getList(JsonArray array) {
        ArrayList<RealmMyLibrary> libraries = new ArrayList<>();
        for (JsonElement e : array
        ) {
            String id = e.getAsJsonObject().get("_id").getAsString();
            RealmMyLibrary li = mRealm.where(RealmMyLibrary.class).equalTo("id", id).findFirst();
            if (li != null)
                libraries.add(li);
        }
        return libraries;
    }
}
