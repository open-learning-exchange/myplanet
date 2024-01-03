package org.ole.planet.myplanet.ui.userprofile;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.databinding.FragmentAchievementBinding;
import org.ole.planet.myplanet.databinding.LayoutButtonPrimaryBinding;
import org.ole.planet.myplanet.databinding.RowAchievementBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;

import io.realm.Realm;

public class AchievementFragment extends BaseContainerFragment {
    private FragmentAchievementBinding fragmentAchievementBinding;
    private RowAchievementBinding rowAchievementBinding;
    private LayoutButtonPrimaryBinding layoutButtonPrimaryBinding;
    Realm mRealm;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentAchievementBinding = FragmentAchievementBinding.inflate(inflater, container, false);
        mRealm = new DatabaseService(MainApplication.context).getRealmInstance();
        user = new UserProfileDbHandler(MainApplication.context).getUserModel();
        fragmentAchievementBinding.btnEdit.setOnClickListener(vi -> {
            if (listener != null) listener.openCallFragment(new EditAchievementFragment());
        });

        return fragmentAchievementBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        achievement = mRealm.where(RealmAchievement.class).equalTo("_id", user.id + "@" + user.planetCode).findFirst();
        fragmentAchievementBinding.tvFirstName.setText(user.firstName);
        fragmentAchievementBinding.tvName.setText(String.format("%s %s %s", user.firstName, user.middleName, user.lastName));
        if (achievement != null) {
            fragmentAchievementBinding.tvGoals.setText(achievement.goals);
            fragmentAchievementBinding.tvPurpose.setText(achievement.purpose);
            fragmentAchievementBinding.tvAchievementHeader.setText(achievement.achievementsHeader);
            fragmentAchievementBinding.llAchievement.removeAllViews();
            for (String s : achievement.achievements) {
                rowAchievementBinding = RowAchievementBinding.inflate(LayoutInflater.from(MainApplication.context));
                JsonElement ob = new Gson().fromJson(s, JsonElement.class);
                if (ob instanceof JsonObject) {
                    rowAchievementBinding.tvDescription.setText(JsonUtils.getString("description", ob.getAsJsonObject()));
                    rowAchievementBinding.tvDate.setText(JsonUtils.getString("date", ob.getAsJsonObject()));
                    rowAchievementBinding.tvTitle.setText(JsonUtils.getString("title", ob.getAsJsonObject()));
                    ArrayList<RealmMyLibrary> libraries = getList(((JsonObject) ob).getAsJsonArray("resources"));
                    if (!JsonUtils.getString("description", ob.getAsJsonObject()).isEmpty() && libraries.size() > 0) {
                        rowAchievementBinding.llRow.setOnClickListener(view -> {
                            rowAchievementBinding.llDesc.setVisibility(rowAchievementBinding.llDesc.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
                            rowAchievementBinding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, (rowAchievementBinding.llDesc.getVisibility() == View.GONE ? R.drawable.ic_down : R.drawable.ic_up), 0);
                        });
                        for (RealmMyLibrary lib : libraries) {
                            layoutButtonPrimaryBinding = LayoutButtonPrimaryBinding.inflate(LayoutInflater.from(MainApplication.context));
                            layoutButtonPrimaryBinding.getRoot().setText(lib.title);
                            layoutButtonPrimaryBinding.getRoot().setCompoundDrawablesWithIntrinsicBounds(0, 0, (lib.isResourceOffline() ? R.drawable.ic_eye : R.drawable.ic_download), 0);
                            layoutButtonPrimaryBinding.getRoot().setOnClickListener(view -> {
                                if (lib.isResourceOffline()) {
                                    openResource(lib);
                                } else {
                                    ArrayList<String> a = new ArrayList<>();
                                    a.add(Utilities.getUrl(lib, settings));
                                    startDownload(a);
                                }
                            });
                            rowAchievementBinding.flexboxResources.addView(layoutButtonPrimaryBinding.getRoot());
                        }
                    } else {
                        rowAchievementBinding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                        createAchievementList();
                        fragmentAchievementBinding.rvOtherInfo.setLayoutManager(new LinearLayoutManager(MainApplication.context));
                        fragmentAchievementBinding.rvOtherInfo.setAdapter(new AdapterOtherInfo(MainApplication.context, achievement.getreferences()));
                    }
                    mRealm.addChangeListener(realm -> {
                        if (fragmentAchievementBinding.llAchievement != null) fragmentAchievementBinding.llAchievement.removeAllViews();
                        createAchievementList();
                    });
                } else {
                    rowAchievementBinding.getRoot().setVisibility(View.GONE);
                }
                fragmentAchievementBinding.llAchievement.addView(rowAchievementBinding.getRoot());
            }
            fragmentAchievementBinding.rvOtherInfo.setLayoutManager(new LinearLayoutManager(MainApplication.context));
            fragmentAchievementBinding.rvOtherInfo.setAdapter(new AdapterOtherInfo(MainApplication.context, achievement.getreferences()));
        }
    }

    private void createAchievementList() {
        for (String s : achievement.achievements) {
            rowAchievementBinding = RowAchievementBinding.inflate(LayoutInflater.from(MainApplication.context));
            JsonElement ob = new Gson().fromJson(s, JsonElement.class);
            if (ob instanceof JsonObject) {
                rowAchievementBinding.tvDescription.setText(JsonUtils.getString("description", ob.getAsJsonObject()));
                rowAchievementBinding.tvDate.setText(JsonUtils.getString("date", ob.getAsJsonObject()));
                rowAchievementBinding.tvTitle.setText(JsonUtils.getString("title", ob.getAsJsonObject()));
                ArrayList<RealmMyLibrary> libraries = getList(((JsonObject) ob).getAsJsonArray("resources"));
                rowAchievementBinding.llRow.setOnClickListener(view -> {
                    rowAchievementBinding.llDesc.setVisibility(rowAchievementBinding.llDesc.getVisibility() == View.GONE ? View.VISIBLE : View.GONE);
                    rowAchievementBinding.tvTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, (rowAchievementBinding.llDesc.getVisibility() == View.GONE ? R.drawable.ic_down : R.drawable.ic_up), 0);
                });
                for (RealmMyLibrary lib : libraries) {
                    layoutButtonPrimaryBinding = LayoutButtonPrimaryBinding.inflate(LayoutInflater.from(MainApplication.context));
                    layoutButtonPrimaryBinding.getRoot().setText(lib.title);
                    layoutButtonPrimaryBinding.getRoot().setCompoundDrawablesWithIntrinsicBounds(0, 0, (lib.isResourceOffline() ? R.drawable.ic_eye : R.drawable.ic_download), 0);
                    layoutButtonPrimaryBinding.getRoot().setOnClickListener(view -> {
                        if (lib.isResourceOffline()) {
                            openResource(lib);
                        } else {
                            ArrayList<String> a = new ArrayList<>();
                            a.add(Utilities.getUrl(lib, settings));
                            startDownload(a);
                        }
                    });
                    rowAchievementBinding.flexboxResources.addView(layoutButtonPrimaryBinding.getRoot());
                }
            } else {
                rowAchievementBinding.getRoot().setVisibility(View.GONE);
            }
            fragmentAchievementBinding.llAchievement.addView(rowAchievementBinding.getRoot());
        }
    }

    private ArrayList<RealmMyLibrary> getList(JsonArray array) {
        ArrayList<RealmMyLibrary> libraries = new ArrayList<>();
        for (JsonElement e : array) {
            String id = e.getAsJsonObject().get("_id").getAsString();
            RealmMyLibrary li = mRealm.where(RealmMyLibrary.class).equalTo("id", id).findFirst();
            if (li != null) libraries.add(li);
        }
        return libraries;
    }
}