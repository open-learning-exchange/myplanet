package org.ole.planet.myplanet.ui.userprofile;


import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class AchievementFragment extends BaseContainerFragment {

    TextView tvGoal, tvAchievement, tvPurpose, tvName, tvFirstName;
    RecyclerView rvOther;
    Realm mRealm;
    LinearLayout llAchievement;
    RealmUserModel user;
    OnHomeItemClickListener listener;
    RealmAchievement achievement;

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
        mRealm = new DatabaseService(MainApplication.context).getRealmInstance();
        user = new UserProfileDbHandler(MainApplication.context).getUserModel();
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
        achievement = mRealm.where(RealmAchievement.class).equalTo("_id", user.getId() + "@" + user.getPlanetCode()).findFirst();
        tvFirstName.setText(user.getFirstName());
        tvName.setText(String.format("%s %s %s", user.getFirstName(), user.getMiddleName(), user.getLastName()));
        if (achievement != null) {
            tvGoal.setText(achievement.getGoals());
            tvPurpose.setText(achievement.getPurpose());
            tvAchievement.setText(achievement.getAchievementsHeader());
            llAchievement.removeAllViews();
            for (String s : achievement.getAchievements()) {
                View v = LayoutInflater.from(MainApplication.context).inflate(R.layout.row_achievement, null);
                createView(v, s);
                llAchievement.addView(v);
            }
            rvOther.setLayoutManager(new LinearLayoutManager(MainApplication.context));
            rvOther.setAdapter(new AdapterOtherInfo(MainApplication.context, achievement.getreferences()));
        }
    }

    private void createView(View v, String s) {
        JsonElement ob = new Gson().fromJson(s, JsonElement.class);
        if (ob instanceof JsonObject) {
            populateAchievementList(ob, v);
        } else {
            v.setVisibility(View.GONE);
        }
    }

    private void populateAchievementList(JsonElement ob, View v) {
        TextView title = v.findViewById(R.id.tv_title);
        TextView date = v.findViewById(R.id.tv_date);
        TextView description = v.findViewById(R.id.tv_description);
        LinearLayout llRow = v.findViewById(R.id.ll_row);
        LinearLayout llDesc = v.findViewById(R.id.ll_desc);
        FlexboxLayout flexboxLayout = v.findViewById(R.id.flexbox_resources);
        description.setText(JsonUtils.getString("description", ob.getAsJsonObject()));
        date.setText(JsonUtils.getString("date", ob.getAsJsonObject()));
        title.setText(JsonUtils.getString("title", ob.getAsJsonObject()));
        ArrayList<RealmMyLibrary> libraries = getList(((JsonObject) ob).getAsJsonArray("resources"));
        if (!JsonUtils.getString("description", ob.getAsJsonObject()).isEmpty() && libraries.size() > 0) {
            llRow.setOnClickListener(view -> {
                llDesc.setVisibility(llDesc.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
                title.setCompoundDrawablesWithIntrinsicBounds(0, 0, (llDesc.getVisibility() == View.GONE ? R.drawable.ic_down : R.drawable.ic_up), 0);
            });
            showResourceButtons(flexboxLayout, libraries);
        } else {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            createAchievementList();
            rvOther.setLayoutManager(new LinearLayoutManager(MainApplication.context));
            rvOther.setAdapter(new AdapterOtherInfo(MainApplication.context, achievement.getreferences()));
        }
        mRealm.addChangeListener(realm -> {
            if (llAchievement != null)
                llAchievement.removeAllViews();
            createAchievementList();
        });
    }


    private void createAchievementList() {
        for (String s : achievement.getAchievements()) {
            View v = LayoutInflater.from(MainApplication.context).inflate(R.layout.row_achievement, null);
            TextView title = v.findViewById(R.id.tv_title);
            TextView date = v.findViewById(R.id.tv_date);
            TextView description = v.findViewById(R.id.tv_description);
            LinearLayout llRow = v.findViewById(R.id.ll_row);
            LinearLayout llDesc = v.findViewById(R.id.ll_desc);
            FlexboxLayout flexboxLayout = v.findViewById(R.id.flexbox_resources);
            JsonElement ob = new Gson().fromJson(s, JsonElement.class);
            if (ob instanceof JsonObject) {
                description.setText(JsonUtils.getString("description", ob.getAsJsonObject()));
                date.setText(JsonUtils.getString("date", ob.getAsJsonObject()));
                title.setText(JsonUtils.getString("title", ob.getAsJsonObject()));
                ArrayList<RealmMyLibrary> libraries = getList(((JsonObject) ob).getAsJsonArray("resources"));
                llRow.setOnClickListener(view -> {
                    llDesc.setVisibility(llDesc.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
                    title.setCompoundDrawablesWithIntrinsicBounds(0, 0, (llDesc.getVisibility() == View.GONE ? R.drawable.ic_down : R.drawable.ic_up), 0);
                });
                showResourceButtons(flexboxLayout, libraries);
            } else {
                v.setVisibility(View.GONE);
            }
            llAchievement.addView(v);
        }

    }

    private void showResourceButtons(FlexboxLayout flexboxLayout, ArrayList<RealmMyLibrary> libraries) {
        for (RealmMyLibrary lib : libraries
        ) {
            Button b = (Button) LayoutInflater.from(MainApplication.context).inflate(R.layout.layout_button_primary, null);
            b.setText(lib.getTitle());
            b.setCompoundDrawablesWithIntrinsicBounds(0, 0, (lib.isResourceOffline() ? R.drawable.ic_eye : R.drawable.ic_download), 0);
            b.setOnClickListener(view -> {
                if (lib.isResourceOffline()) {
                    openResource(lib);
                } else {
                    ArrayList<String> a = new ArrayList<>();
                    a.add(Utilities.getUrl(lib, settings));
                    startDownload(a);
                }
            });
            flexboxLayout.addView(b);
        }
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
