package org.ole.planet.myplanet.ui.userprofile;


import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class EditAchievementFragment extends BaseContainerFragment {

    EditText etPurpose, etGoals, etAchievement;
    Button btnAddAchievement, btnOther, btnUpdate, btnCancel;
    Realm mRealm;
    RealmUserModel user;
    RealmAchievement achievement;
    LinearLayout llachievement, llOthers;
    JsonArray otherInfoArray, achievementArray, resourceArray;
    CheckBox checkBox;

    public EditAchievementFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_edit_achievement, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        achievementArray = new JsonArray();
        achievement = mRealm.where(RealmAchievement.class).equalTo("_id", user.getId() + "@" + user.getPlanetCode()).findFirst();
        createView(v);
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        if (achievement == null) {
            achievementArray = new JsonArray();
            otherInfoArray = new JsonArray();
            achievement = mRealm.createObject(RealmAchievement.class, user.getId() + "@" + user.getPlanetCode());
        } else {
            achievementArray = achievement.getAchievementsArray();
            otherInfoArray = achievement.getOtherInfoArray();
            etAchievement.setText(achievement.getAchievementsHeader());
            etPurpose.setText(achievement.getPurpose());
            etGoals.setText(achievement.getGoals());
            checkBox.setChecked(Boolean.parseBoolean(achievement.getSendToNation()));
        }

        resourceArray = new JsonArray();
        setListeners();
        return v;
    }

    private void setListeners() {
        btnUpdate.setOnClickListener(view -> {
            String goals = etGoals.getText().toString();
            String purpose = etPurpose.getText().toString();
            String achie = etAchievement.getText().toString();
            achievement.setAchievementsHeader(achie);
            achievement.setGoals(goals);
            achievement.setPurpose(purpose);
            achievement.setAchievements(achievementArray);
            achievement.setSendToNation(checkBox.isChecked() + "");
            achievement.setOtherInfo(otherInfoArray);
            getActivity().onBackPressed();
        });
        btnCancel.setOnClickListener(view -> getActivity().onBackPressed());
        btnAddAchievement.setOnClickListener(vi -> {
            showAddachievementAlert();
        });
        btnOther.setOnClickListener(view -> {
            showOtherInfoDialog();
        });
    }

    private void createView(View v) {
        etGoals = v.findViewById(R.id.et_goals);
        llachievement = v.findViewById(R.id.ll_attachment);
        llOthers = v.findViewById(R.id.ll_other_info);
        etPurpose = v.findViewById(R.id.et_purpose);
        etAchievement = v.findViewById(R.id.et_achievement);
        btnAddAchievement = v.findViewById(R.id.btn_achievement);
        btnOther = v.findViewById(R.id.btn_other);
        btnUpdate = v.findViewById(R.id.btn_update);
        btnCancel = v.findViewById(R.id.btn_cancel);
        checkBox = v.findViewById(R.id.cb_send_to_nation);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showAchievementAndInfo();
        showOtherInfo();

    }

    private void showAchievementAndInfo() {
        ChipCloudConfig config = Utilities.getCloudConfig()
                .selectMode(ChipCloud.SelectMode.single);
        llOthers.removeAllViews();
        llachievement.removeAllViews();
        for (JsonElement e : achievementArray) {
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.edit_attachement, null);
            ((TextView) v.findViewById(R.id.tv_title)).setText(e.getAsJsonObject().get("description").getAsString());
            FlexboxLayout flexboxLayout = v.findViewById(R.id.flexbox);
            flexboxLayout.removeAllViews();
            final ChipCloud chipCloud = new ChipCloud(getActivity(), flexboxLayout, config);
            for (JsonElement element : e.getAsJsonObject().getAsJsonArray("resources")) {
                chipCloud.addChip(element.getAsJsonObject().get("title").getAsString());
            }
            v.findViewById(R.id.iv_delete).setOnClickListener(view -> {
                achievementArray.remove(e);
                showAchievementAndInfo();
            });
            llachievement.addView(v);
        }

    }

    private void showOtherInfo() {
        for (JsonElement e : otherInfoArray) {
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.edit_other_info, null);
            ((TextView) v.findViewById(R.id.tv_title)).setText(e.getAsJsonObject().get("type").getAsString() + " : " + e.getAsJsonObject().get("description").getAsString());
            v.findViewById(R.id.iv_delete).setOnClickListener(view -> {
                otherInfoArray.remove(e);
                showAchievementAndInfo();
            });
            llOthers.addView(v);
        }
    }

    private void showOtherInfoDialog() {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_other_info, null);
        Spinner spnType = v.findViewById(R.id.spn_type);
        EditText etDesc = v.findViewById(R.id.et_description);
        new AlertDialog.Builder(getActivity())
                .setTitle("Add Other Information")
                .setIcon(R.drawable.ic_edit)
                .setView(v)
                .setPositiveButton("Submit", (dialogInterface, i) -> {
                    String type = spnType.getSelectedItem().toString();
                    String desc = etDesc.getText().toString();
                    if (desc.isEmpty()) {
                        Utilities.toast(getActivity(), "Description is required.");
                        return;
                    }
                    JsonObject ob = new JsonObject();
                    ob.addProperty("type", type);
                    ob.addProperty("description", desc);
                    otherInfoArray.add(ob);
                    showAchievementAndInfo();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showAddachievementAlert() {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_add_attachment, null);
        Button btnAddResource = v.findViewById(R.id.btn_add_resources);
        EditText etDescription = v.findViewById(R.id.et_desc);
        resourceArray = new JsonArray();
        btnAddResource.setOnClickListener(view -> showRecourseListDialog(resourceArray));
        new AlertDialog.Builder(getActivity()).setTitle("Add Achievement")
                .setIcon(R.drawable.ic_edit)
                .setView(v)
                .setCancelable(false)
                .setPositiveButton("Submit", (dialogInterface, i) -> {
                    String desc = etDescription.getText().toString();
                    if (desc.isEmpty()) {
                        Toast.makeText(getActivity(), "Description is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JsonObject object = new JsonObject();
                    object.addProperty("description", desc);
                    object.add("resources", resourceArray);
                    achievementArray.add(object);
                    showAchievementAndInfo();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showRecourseListDialog(JsonArray resourceArray) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Select resources : ");
        List<RealmMyLibrary> list = mRealm.where(RealmMyLibrary.class).findAll();
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.my_library_alertdialog, null);

        CheckboxListView lv = v.findViewById(R.id.alertDialog_listView);
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            names.add(list.get(i).getTitle());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity().getBaseContext(), R.layout.rowlayout, R.id.checkBoxRowLayout, names);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setAdapter(adapter);
        builder.setView(v);
        builder.setPositiveButton("Ok", (dialogInterface, i) -> {
            ArrayList<Integer> items = lv.getSelectedItemsList();
            for (int ii : items
            ) {
                resourceArray.add(list.get(ii).serializeResource());
            }
        }).setNegativeButton("Cancel", null).show();
    }

}
