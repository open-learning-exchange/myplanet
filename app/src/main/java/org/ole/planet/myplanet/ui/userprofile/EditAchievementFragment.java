package org.ole.planet.myplanet.ui.userprofile;


import android.app.DatePickerDialog;
import android.os.Bundle;

import com.google.android.material.textfield.TextInputLayout;

import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;

/**
 * A simple {@link Fragment} subclass.
 */
public class EditAchievementFragment extends BaseAchievementFragment implements DatePickerDialog.OnDateSetListener {


    public EditAchievementFragment() {
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
        initializeData();
        setListeners();
        if (achievementArray != null) showAchievementAndInfo();
        if (referenceArray != null) showreference();
        return v;
    }

    private void setListeners() {
        btnUpdate.setOnClickListener(view -> {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            setUserInfo();
            setAchievementInfo();
            getActivity().onBackPressed();
            mRealm.commitTransaction();
        });
        btnCancel.setOnClickListener(view -> getActivity().onBackPressed());
        btnAddAchievement.setOnClickListener(vi -> showAddachievementAlert(null));
        btnOther.setOnClickListener(view -> showreferenceDialog(null));
        tvDob.setOnClickListener(view -> {
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = new DatePickerDialog(getActivity(), this, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMaxDate(Calendar.getInstance().getTimeInMillis());
            dpd.show();
        });
    }


    private void createView(View v) {
        etGoals = v.findViewById(R.id.et_goals);
        llachievement = v.findViewById(R.id.ll_attachment);
        llOthers = v.findViewById(R.id.ll_other_info);
        etPurpose = v.findViewById(R.id.et_purpose);
        etAchievement = v.findViewById(R.id.et_achievement);
        etName = v.findViewById(R.id.et_fname);
        etMiddleName = v.findViewById(R.id.et_mname);
        etLastName = v.findViewById(R.id.et_lname);
        etBirthPlace = v.findViewById(R.id.et_birthplace);
        tvDob = v.findViewById(R.id.txt_dob);
        btnAddAchievement = v.findViewById(R.id.btn_achievement);
        btnOther = v.findViewById(R.id.btn_other);
        btnUpdate = v.findViewById(R.id.btn_update);
        btnCancel = v.findViewById(R.id.btn_cancel);
        checkBox = v.findViewById(R.id.cb_send_to_nation);

    }


    private void showAchievementAndInfo() {
        ChipCloudConfig config = Utilities.getCloudConfig()
                .selectMode(ChipCloud.SelectMode.single);
        llachievement.removeAllViews();
        for (JsonElement e : achievementArray) {
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.edit_attachement, null);
            ((TextView) v.findViewById(R.id.tv_title)).setText(e.getAsJsonObject().get("title").getAsString());
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
            v.findViewById(R.id.edit).setOnClickListener(V -> {
                showAddachievementAlert(e.getAsJsonObject());
            });
            llachievement.addView(v);
        }

    }

    private void showreference() {
        llOthers.removeAllViews();

        for (JsonElement e : referenceArray) {
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.edit_other_info, null);
            ((TextView) v.findViewById(R.id.tv_title)).setText(e.getAsJsonObject().get("name").getAsString());
            v.findViewById(R.id.iv_delete).setOnClickListener(view -> {
                referenceArray.remove(e);
                showreference();
            });
            v.findViewById(R.id.edit).setOnClickListener(vi -> {
                showreferenceDialog(e.getAsJsonObject());
            });
            llOthers.addView(v);
        }
    }

    private void showreferenceDialog(JsonObject object) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_reference, null);
        EditText etName = v.findViewById(R.id.et_name);
        TextInputLayout tlName = v.findViewById(R.id.tl_name);
        EditText etRelation = v.findViewById(R.id.et_relationship);
        EditText etPhone = v.findViewById(R.id.et_phone);
        EditText etEmail = v.findViewById(R.id.et_email);
        EditText[] ar = {etName, etPhone, etEmail, etRelation};
        setPrevReference(ar, object);
        AlertDialog d = DialogUtils.getAlertDialog(getActivity(), "Add Reference", v);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                tlName.setError("Name is required.");
                return;
            }
            if (object != null)
                referenceArray.remove(object);
            if (referenceArray == null)
                referenceArray = new JsonArray();
            referenceArray.add(RealmAchievement.createReference(name, etRelation, etPhone, etEmail));
            showreference();
            d.dismiss();
        });
    }


    private void setPrevReference(EditText[] ar, JsonObject object) {
        if (object != null) {
            ar[0].setText(object.get("name").getAsString());
            ar[1].setText(object.get("phone").getAsString());
            ar[2].setText(object.get("email").getAsString());
            ar[3].setText(object.get("relationship").getAsString());
        }
    }


    String date = "";

    private void showAddachievementAlert(JsonObject object) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_add_attachment, null);
        Button btnAddResource = v.findViewById(R.id.btn_add_resources);
        EditText etDescription = v.findViewById(R.id.et_desc);
        EditText etTitle = v.findViewById(R.id.et_title);
        AppCompatTextView tvDate = v.findViewById(R.id.tv_date);
        initAchievementDatePicker(tvDate);
        resourceArray = new JsonArray();
        List<String> prevList = setUpOldAchievement(object, etDescription, etTitle, tvDate);
        btnAddResource.setOnClickListener(view -> showResourseListDialog(prevList));
        new AlertDialog.Builder(getActivity()).setTitle("Add Achievement").setIcon(R.drawable.ic_edit).setView(v).setCancelable(false)
                .setPositiveButton("Submit", (dialogInterface, i) -> {
                    String desc = etDescription.getText().toString().trim();
                    String title = etTitle.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(getActivity(), "Title is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (object != null)
                        achievementArray.remove(object);
                    saveAchievement(desc, title);
                }).setNegativeButton("Cancel", null).show();
    }

    private List<String> setUpOldAchievement(JsonObject object, EditText etDescription, EditText etTitle, AppCompatTextView tvDate) {
        List<String> prevList = new ArrayList<>();
        if (object != null) {
            etTitle.setText(object.get("title").getAsString());
            etDescription.setText(object.get("description").getAsString());
            tvDate.setText(object.get("date").getAsString());
            JsonArray array = object.getAsJsonArray("resources");
            date = object.get("date").getAsString();
            for (JsonElement o : array
            ) {
                prevList.add(o.getAsJsonObject().get("title").getAsString());
            }
            resourceArray = object.getAsJsonArray("resources");
        }
        return prevList;
    }

    private void initAchievementDatePicker(TextView tvDate) {
        tvDate.setOnClickListener(view -> {
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = new DatePickerDialog(getActivity(), (datePicker, i, i1, i2) -> {
                date = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2);
                tvDate.setText(date);
            }, now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMaxDate(now.getTimeInMillis());
            dpd.show();
        });
    }

    private void saveAchievement(String desc, String title) {
        JsonObject object = new JsonObject();
        object.addProperty("description", desc);
        object.addProperty("title", title);
        object.addProperty("date", date);
        object.add("resources", resourceArray);
        achievementArray.add(object);
        showAchievementAndInfo();
    }

    private void showResourseListDialog(List<String> prevList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Select resources : ");
        List<RealmMyLibrary> list = mRealm.where(RealmMyLibrary.class).findAll();
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.my_library_alertdialog, null);
        CheckboxListView lv = createResourceList(v, list, prevList);
        builder.setView(v);
        builder.setPositiveButton("Ok", (dialogInterface, i) -> {
            ArrayList<Integer> items = lv.getSelectedItemsList();
            resourceArray = new JsonArray();
            for (int ii : items) {
                resourceArray.add(list.get(ii).serializeResource());
            }
        }).setNegativeButton("Cancel", null).show();
    }


    @Override
    public void onDateSet(DatePicker datePicker, int i, int i1, int i2) {
        tvDob.setText(String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2));
    }
}
