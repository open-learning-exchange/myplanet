package org.ole.planet.myplanet.ui.userprofile;

import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.JsonArray;

import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmUserModel;

import io.realm.Realm;

public class BaseAchievementFragment extends BaseContainerFragment {
    EditText etPurpose, etGoals, etAchievement, etName, etMiddleName, etLastName, etBirthPlace;
    Button btnAddAchievement, btnOther, btnUpdate, btnCancel;
    TextView tvDob;
    Realm mRealm;
    RealmUserModel user;
    RealmAchievement achievement;
    LinearLayout llachievement, llOthers;
    JsonArray referenceArray, achievementArray, resourceArray;
    CheckBox checkBox;
    String dob = "";


    public void initializeData() {
        if (achievement == null) {
            achievement = mRealm.createObject(RealmAchievement.class, user.getId() + "@" + user.getPlanetCode());
        } else {
            achievementArray = achievement.getAchievementsArray();
            referenceArray = achievement.getreferencesArray();
            etAchievement.setText(achievement.getAchievementsHeader());
            etPurpose.setText(achievement.getPurpose());
            etGoals.setText(achievement.getGoals());
            checkBox.setChecked(Boolean.parseBoolean(achievement.getSendToNation()));
        }
        tvDob.setText(TextUtils.isEmpty(user.getDob()) ? "Birth Date" : user.getDob());
        resourceArray = new JsonArray();
        etName.setText(user.getFirstName());
        etMiddleName.setText(user.getMiddleName());
        etLastName.setText(user.getLastName());
        etBirthPlace.setText(user.getBirthPlace());
    }


    public void setUserInfo() {
        user.setFirstName(etName.getText().toString());
        user.setMiddleName(etMiddleName.getText().toString());
        user.setLastName(etLastName.getText().toString());
        user.setDob(tvDob.getText().toString());
        user.setBirthPlace(etBirthPlace.getText().toString());
    }

    public void setAchievementInfo() {
        achievement.setAchievementsHeader(etAchievement.getText().toString());
        achievement.setGoals(etGoals.getText().toString());
        achievement.setPurpose(etPurpose.getText().toString());
        achievement.setAchievements(achievementArray);
        achievement.setreferences(referenceArray);
        achievement.setSendToNation(checkBox.isChecked() + "");
    }
}
