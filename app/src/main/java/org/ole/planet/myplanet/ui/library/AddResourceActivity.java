package org.ole.planet.myplanet.ui.library;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ActivityAddResourceBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;

public class AddResourceActivity extends AppCompatActivity {
    private ActivityAddResourceBinding activityAddResourceBinding;
    Realm mRealm;
    RealmUserModel userModel;
    RealmList<String> subjects;
    RealmList<String> levels;
    RealmList<String> resourceFor;
    String resourceUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityAddResourceBinding = ActivityAddResourceBinding.inflate(getLayoutInflater());
        setContentView(activityAddResourceBinding.getRoot());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        userModel = new UserProfileDbHandler(this).getUserModel();
        resourceUrl = getIntent().getStringExtra("resource_local_url");
        levels = new RealmList<>();
        subjects = new RealmList<>();
        resourceFor = new RealmList<>();
        mRealm = new DatabaseService(this).getRealmInstance();
        initializeViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRealm != null && !mRealm.isClosed()) mRealm.close();
    }

    private void initializeViews() {
        activityAddResourceBinding.fileUrl.setText(getString(R.string.file) + resourceUrl);
        activityAddResourceBinding.tvAddedBy.setText(userModel.getName());
        activityAddResourceBinding.tvLevels.setOnClickListener(view -> showMultiSelectList(getResources().getStringArray(R.array.array_levels), levels, view));
        activityAddResourceBinding.tvSubject.setOnClickListener(view -> showMultiSelectList(getResources().getStringArray(R.array.array_subjects), subjects, view));
        activityAddResourceBinding.tvResourceFor.setOnClickListener(view -> showMultiSelectList(getResources().getStringArray(R.array.array_resource_for), subjects, view));
        activityAddResourceBinding.btnSubmit.setOnClickListener(view -> saveResource());
        activityAddResourceBinding.btnCancel.setOnClickListener(view -> finish());
    }

    private void saveResource() {
        String title = activityAddResourceBinding.etTitle.getText().toString().trim();
        if (!validate(title)) return;
        mRealm.executeTransactionAsync(realm -> {
            String id = UUID.randomUUID().toString();
            RealmMyLibrary resource = realm.createObject(RealmMyLibrary.class, id);
            resource.setTitle(title);
            createResource(resource, id);
        }, () -> {
            Utilities.toast(AddResourceActivity.this, getString(R.string.resource_saved_successfully));
            finish();
        });
    }

    private void createResource(RealmMyLibrary resource, String id) {
        resource.setAddedBy(activityAddResourceBinding.tvAddedBy.getText().toString().trim());
        resource.setAuthor(activityAddResourceBinding.etAuthor.getText().toString().trim());
        resource.setResource_id(id);
        resource.setYear(activityAddResourceBinding.etYear.getText().toString().trim());
        resource.setDescription(activityAddResourceBinding.etDescription.getText().toString().trim());
        resource.setPublisher(activityAddResourceBinding.etPublisher.getText().toString().trim());
        resource.setLinkToLicense(activityAddResourceBinding.etLinkToLicense.getText().toString().trim());
        resource.setOpenWith(activityAddResourceBinding.spnOpenWith.getSelectedItem().toString());
        resource.setLanguage(activityAddResourceBinding.spnLang.getSelectedItem().toString());
        resource.setMediaType(activityAddResourceBinding.spnMedia.getSelectedItem().toString());
        resource.setResourceType(activityAddResourceBinding.spnResourceType.getSelectedItem().toString());
        resource.setSubject(subjects);
        resource.setUserId(new RealmList<>());
        resource.setLevel(levels);
        resource.setCreatedDate(Calendar.getInstance().getTimeInMillis());
        resource.setResourceFor(resourceFor);
        resource.setResourceLocalAddress(resourceUrl);
        resource.setResourceOffline(true);
        resource.setFilename(resourceUrl.substring(resourceUrl.lastIndexOf("/")));
    }

    private boolean validate(String title) {
        if (title.isEmpty()) {
            activityAddResourceBinding.tlTitle.setError(getString(R.string.title_is_required));
            return false;
        }
        if (levels.isEmpty()) {
            Utilities.toast(this, getString(R.string.level_is_required));
            return false;
        }
        if (subjects.isEmpty()) {
            Utilities.toast(this, getString(R.string.subject_is_required));
            return false;
        }
        return true;
    }

    private void showMultiSelectList(String[] list, List<String> items, View view) {
        CheckboxListView listView = new CheckboxListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.rowlayout, R.id.checkBoxRowLayout, list);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(adapter);
        new AlertDialog.Builder(this).setView(listView).setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            ArrayList<Integer> selected = listView.getSelectedItemsList();
            items.clear();
            String selection = "";
            for (Integer index : selected) {
                String s = list[index];
                selection += s + " ,";
                items.add(s);
            }
            ((TextView) view).setText(selection);
        }).setNegativeButton(R.string.dismiss, null).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }
}
