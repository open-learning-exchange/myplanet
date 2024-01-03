package org.ole.planet.myplanet.ui.userprofile;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.databinding.AlertAddAttachmentBinding;
import org.ole.planet.myplanet.databinding.AlertReferenceBinding;
import org.ole.planet.myplanet.databinding.EditAttachementBinding;
import org.ole.planet.myplanet.databinding.EditOtherInfoBinding;
import org.ole.planet.myplanet.databinding.FragmentEditAchievementBinding;
import org.ole.planet.myplanet.databinding.MyLibraryAlertdialogBinding;
import org.ole.planet.myplanet.databinding.RowlayoutBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAchievement;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.realm.Realm;

public class EditAchievementFragment extends BaseContainerFragment implements DatePickerDialog.OnDateSetListener {
    private FragmentEditAchievementBinding fragmentEditAchievementBinding;
    private EditAttachementBinding editAttachementBinding;
    private EditOtherInfoBinding editOtherInfoBinding;
    private AlertReferenceBinding alertReferenceBinding;
    private AlertAddAttachmentBinding alertAddAttachmentBinding;
    private MyLibraryAlertdialogBinding myLibraryAlertdialogBinding;

    Realm mRealm;
    RealmUserModel user;
    RealmAchievement achievement;

    JsonArray referenceArray, achievementArray, resourceArray;
    public EditAchievementFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentEditAchievementBinding = FragmentEditAchievementBinding.inflate(inflater, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        user = new UserProfileDbHandler(getActivity()).getUserModel();
        achievementArray = new JsonArray();
        achievement = mRealm.where(RealmAchievement.class).equalTo("_id", user.id + "@" + user.planetCode).findFirst();
        initializeData();
        setListeners();
        if (achievementArray != null) showAchievementAndInfo();
        if (referenceArray != null) showreference();
        return fragmentEditAchievementBinding.getRoot();
    }

    private void setListeners() {
        fragmentEditAchievementBinding.btnUpdate.setOnClickListener(view -> {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
            setUserInfo();
            setAchievementInfo();
            getActivity().onBackPressed();
            mRealm.commitTransaction();
        });
        fragmentEditAchievementBinding.btnCancel.setOnClickListener(view -> getActivity().onBackPressed());
        fragmentEditAchievementBinding.btnAchievement.setOnClickListener(vi -> showAddachievementAlert(null));
        fragmentEditAchievementBinding.btnOther.setOnClickListener(view -> showreferenceDialog(null));
        fragmentEditAchievementBinding.txtDob.setOnClickListener(view -> {
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = new DatePickerDialog(getActivity(), this, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMaxDate(Calendar.getInstance().getTimeInMillis());
            dpd.show();
        });
    }

    private void showAchievementAndInfo() {
        ChipCloudConfig config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single);
        fragmentEditAchievementBinding.llAttachment.removeAllViews();
        for (JsonElement e : achievementArray) {
            editAttachementBinding = EditAttachementBinding.inflate(LayoutInflater.from(getActivity()));
            editAttachementBinding.tvTitle.setText(e.getAsJsonObject().get("title").getAsString());
            FlexboxLayout flexboxLayout = editAttachementBinding.flexbox;
            flexboxLayout.removeAllViews();
            final ChipCloud chipCloud = new ChipCloud(getActivity(), flexboxLayout, config);
            for (JsonElement element : e.getAsJsonObject().getAsJsonArray("resources")) {
                chipCloud.addChip(element.getAsJsonObject().get("title").getAsString());
            }
            editAttachementBinding.ivDelete.setOnClickListener(view -> {
                achievementArray.remove(e);
                showAchievementAndInfo();
            });
            editAttachementBinding.edit.setOnClickListener(V -> {
                showAddachievementAlert(e.getAsJsonObject());
            });
            View editAttachementView = editAttachementBinding.getRoot();
            fragmentEditAchievementBinding.llAttachment.addView(editAttachementView);
        }
    }

    private void showreference() {
        fragmentEditAchievementBinding.llOtherInfo.removeAllViews();
        for (JsonElement e : referenceArray) {
            editOtherInfoBinding = EditOtherInfoBinding.inflate(LayoutInflater.from(getActivity()));
            editOtherInfoBinding.tvTitle.setText(e.getAsJsonObject().get("name").getAsString());
            editOtherInfoBinding.ivDelete.setOnClickListener(view -> {
                referenceArray.remove(e);
                showreference();
            });
            editOtherInfoBinding.edit.setOnClickListener(vi -> {
                showreferenceDialog(e.getAsJsonObject());
            });
            View editOtherInfoView = editOtherInfoBinding.getRoot();
            fragmentEditAchievementBinding.llOtherInfo.addView(editOtherInfoView);
        }
    }

    private void showreferenceDialog(JsonObject object) {
        alertReferenceBinding = AlertReferenceBinding.inflate(LayoutInflater.from(getActivity()));
        EditText[] ar = {alertReferenceBinding.etName, alertReferenceBinding.etPhone, alertReferenceBinding.etEmail, alertReferenceBinding.etRelationship};
        setPrevReference(ar, object);
        View alertReferenceView = alertReferenceBinding.getRoot();
        AlertDialog d = DialogUtils.getAlertDialog(getActivity(), getString(R.string.add_reference), alertReferenceView);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = alertReferenceBinding.etName.getText().toString().trim();
            if (name.isEmpty()) {
                alertReferenceBinding.tlName.setError(getString(R.string.name_is_required));
                return;
            }
            if (object != null) referenceArray.remove(object);
            if (referenceArray == null) referenceArray = new JsonArray();
            referenceArray.add(RealmAchievement.createReference(name, alertReferenceBinding.etRelationship, alertReferenceBinding.etPhone, alertReferenceBinding.etEmail));
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
        alertAddAttachmentBinding = AlertAddAttachmentBinding.inflate(LayoutInflater.from(getActivity()));
        alertAddAttachmentBinding.tvDate.setOnClickListener(view -> {
            Calendar now = Calendar.getInstance();
            DatePickerDialog dpd = new DatePickerDialog(getActivity(), (datePicker, i, i1, i2) -> {
                date = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2);
                alertAddAttachmentBinding.tvDate.setText(date);
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMaxDate(now.getTimeInMillis());
            dpd.show();
        });

        resourceArray = new JsonArray();
        List<String> prevList = setUpOldAchievement(object, alertAddAttachmentBinding.etDesc, alertAddAttachmentBinding.etTitle, (AppCompatTextView) alertAddAttachmentBinding.tvDate);
        alertAddAttachmentBinding.btnAddResources.setOnClickListener(view -> showResourseListDialog(prevList));
        View alertAddAttachmentView = alertAddAttachmentBinding.getRoot();
        new AlertDialog.Builder(getActivity()).setTitle(R.string.add_achievement).setIcon(R.drawable.ic_edit).setView(alertAddAttachmentView).setCancelable(false).setPositiveButton("Submit", (dialogInterface, i) -> {
            String desc = alertAddAttachmentBinding.etDesc.getText().toString().trim();
            String title = alertAddAttachmentBinding.etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(getActivity(), getString(R.string.title_is_required), Toast.LENGTH_SHORT).show();
                return;
            }
            if (object != null) achievementArray.remove(object);
            saveAchievement(desc, title);
        }).setNegativeButton(getString(R.string.cancel), null).show();
    }

    private List<String> setUpOldAchievement(JsonObject object, EditText etDescription, EditText etTitle, AppCompatTextView tvDate) {
        List<String> prevList = new ArrayList<>();
        if (object != null) {
            etTitle.setText(object.get("title").getAsString());
            etDescription.setText(object.get("description").getAsString());
            tvDate.setText(object.get("date").getAsString());
            JsonArray array = object.getAsJsonArray("resources");
            date = object.get("date").getAsString();
            for (JsonElement o : array) {
                prevList.add(o.getAsJsonObject().get("title").getAsString());
            }
            resourceArray = object.getAsJsonArray("resources");
        }
        return prevList;
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
        builder.setTitle(R.string.select_resources);
        List<RealmMyLibrary> list = mRealm.where(RealmMyLibrary.class).findAll();
        myLibraryAlertdialogBinding = MyLibraryAlertdialogBinding.inflate(LayoutInflater.from(getActivity()));
        View myLibraryAlertdialogView = myLibraryAlertdialogBinding.getRoot();
        CheckboxListView lv = createResourceList(myLibraryAlertdialogBinding, list, prevList);
        builder.setView(myLibraryAlertdialogView);
        builder.setPositiveButton("Ok", (dialogInterface, i) -> {
            ArrayList<Integer> items = lv.selectedItemsList;
            resourceArray = new JsonArray();
            for (int ii : items) {
                resourceArray.add(list.get(ii).serializeResource());
            }
        }).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onDateSet(DatePicker datePicker, int i, int i1, int i2) {
        fragmentEditAchievementBinding.txtDob.setText(String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2));
    }

    public void initializeData() {
        if (achievement == null) {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
            achievement = mRealm.createObject(RealmAchievement.class, user.id + "@" + user.planetCode);
            return;
        } else {
            achievementArray = achievement.getAchievementsArray();
            referenceArray = achievement.getreferencesArray();
            fragmentEditAchievementBinding.etAchievement.setText(achievement.achievementsHeader);
            fragmentEditAchievementBinding.etPurpose.setText(achievement.purpose);
            fragmentEditAchievementBinding.etGoals.setText(achievement.goals);
            fragmentEditAchievementBinding.cbSendToNation.setChecked(Boolean.parseBoolean(achievement.sendToNation));
        }
        fragmentEditAchievementBinding.txtDob.setText(TextUtils.isEmpty(user.dob) ? getString(R.string.birth_date) : TimeUtils.getFormatedDate(user.dob, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        resourceArray = new JsonArray();
        fragmentEditAchievementBinding.etFname.setText(user.firstName);
        fragmentEditAchievementBinding.etMname.setText(user.middleName);
        fragmentEditAchievementBinding.etLname.setText(user.lastName);
        fragmentEditAchievementBinding.etBirthplace.setText(user.birthPlace);
    }

    public CheckboxListView createResourceList(MyLibraryAlertdialogBinding myLibraryAlertdialogBinding, List<RealmMyLibrary> list, List<String> prevList) {

        ArrayList<String> names = new ArrayList<>();
        ArrayList<Integer> selected = new ArrayList();
        for (int i = 0; i < list.size(); i++) {
            names.add(list.get(i).title);
            if (prevList.contains(list.get(i).title)) selected.add(i);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), R.layout.item_checkbox, R.id.checkBoxRowLayout, names) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                RowlayoutBinding rowlayoutBinding = RowlayoutBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                CheckedTextView textView = rowlayoutBinding.getRoot();
                textView.setText(getItem(position));
                textView.setChecked(myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList.contains(position));
                myLibraryAlertdialogBinding.alertDialogListView.setItemChecked(position, myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList.contains(position));
                return textView;
            }
        };
        myLibraryAlertdialogBinding.alertDialogListView.selectedItemsList = selected;
        myLibraryAlertdialogBinding.alertDialogListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        myLibraryAlertdialogBinding.alertDialogListView.setAdapter(adapter);
        return myLibraryAlertdialogBinding.alertDialogListView;
    }

    public void setUserInfo() {}

    public void setAchievementInfo() {
        achievement.achievementsHeader = fragmentEditAchievementBinding.etAchievement.getText().toString().trim();
        achievement.goals = fragmentEditAchievementBinding.etGoals.getText().toString().trim();
        achievement.purpose = fragmentEditAchievementBinding.etPurpose.getText().toString().trim();
        achievement.setAchievements(achievementArray);
        achievement.setreferences(referenceArray);
        achievement.sendToNation = fragmentEditAchievementBinding.cbSendToNation.isChecked() + "";
    }
}