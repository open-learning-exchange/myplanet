package org.ole.planet.myplanet.ui.userprofile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.JsonArray;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

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
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            achievement = mRealm.createObject(RealmAchievement.class, user.getId() + "@" + user.getPlanetCode());
            return;
        } else {
            achievementArray = achievement.getAchievementsArray();
            referenceArray = achievement.getreferencesArray();
            etAchievement.setText(achievement.getAchievementsHeader());
            etPurpose.setText(achievement.getPurpose());
            etGoals.setText(achievement.getGoals());
            checkBox.setChecked(Boolean.parseBoolean(achievement.getSendToNation()));
        }
        tvDob.setText(TextUtils.isEmpty(user.getDob()) ? "Birth Date" : TimeUtils.getFormatedDate(user.getDob(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        resourceArray = new JsonArray();
        etName.setText(user.getFirstName());
        etMiddleName.setText(user.getMiddleName());
        etLastName.setText(user.getLastName());
        etBirthPlace.setText(user.getBirthPlace());
    }

    public CheckboxListView createResourceList(View v, List<RealmMyLibrary> list, List<String> prevList) {

        CheckboxListView lv = v.findViewById(R.id.alertDialog_listView);
        ArrayList<String> names = new ArrayList<>();
        ArrayList<Integer> selected = new ArrayList();
        for (int i = 0; i < list.size(); i++) {
            names.add(list.get(i).getTitle());
            if (prevList.contains(list.get(i).getTitle()))
                selected.add(i);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.item_checkbox, R.id.checkBoxRowLayout, names) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                CheckedTextView textView = (CheckedTextView) LayoutInflater.from(getActivity()).inflate(R.layout.rowlayout, parent, false);
                textView.setText(getItem(position));
                textView.setChecked(lv.getSelectedItemsList().contains(position));
                lv.setItemChecked(position, lv.getSelectedItemsList().contains(position));
                return textView;
            }
        };
        lv.setSelectedItemsList(selected);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setAdapter(adapter);
        return lv;
    }


    public void setUserInfo() {
        //user.setFirstName(etName.getText().toString());
        //user.setMiddleName(etMiddleName.getText().toString());
        //user.setLastName(etLastName.getText().toString());
        //user.setDob(tvDob.getText().toString());
        //user.setBirthPlace(etBirthPlace.getText().toString());
    }

    public void setAchievementInfo() {
        achievement.setAchievementsHeader(etAchievement.getText().toString().trim());
        achievement.setGoals(etGoals.getText().toString().trim());
        achievement.setPurpose(etPurpose.getText().toString().trim());
        achievement.setAchievements(achievementArray);
        achievement.setreferences(referenceArray);
        achievement.setSendToNation(checkBox.isChecked() + "");
    }
}
