package org.ole.planet.myplanet.ui.library;

import android.content.DialogInterface;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;

public class AddResourceActivity extends AppCompatActivity {

    EditText etTitle, etAuthor, etYear, etDescription, etPublisher, etLinkToLicense, etOpenWhich;
    Spinner spnLang, spnMedia, spnResourceType, spnOpenWith;
    TextView tvSubjects, tvLevels, tvResourceFor, tvAddedBy, fileUrl;
    Realm mRealm;
    RealmUserModel userModel;
    RealmList<String> subjects;
    RealmList<String> levels;
    RealmList<String> resourceFor;
    String resourceUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_resource);
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
        if (mRealm != null && !mRealm.isClosed())
            mRealm.close();
    }

    private void initializeViews() {
        etTitle = findViewById(R.id.et_title);
        etAuthor = findViewById(R.id.et_author);
        fileUrl = findViewById(R.id.file_url);
        etYear = findViewById(R.id.et_year);
        etDescription = findViewById(R.id.et_description);
        etPublisher = findViewById(R.id.et_publisher);
        etLinkToLicense = findViewById(R.id.et_link_to_license);
        etOpenWhich = findViewById(R.id.et_open_which);
        spnLang = findViewById(R.id.spn_lang);
        spnMedia = findViewById(R.id.spn_media);
        spnResourceType = findViewById(R.id.spn_resource_type);
        spnOpenWith = findViewById(R.id.spn_open_with);
        tvSubjects = findViewById(R.id.tv_subject);
        tvAddedBy = findViewById(R.id.tv_added_by);
        tvLevels = findViewById(R.id.tv_levels);
        tvResourceFor = findViewById(R.id.tv_resource_for);
        fileUrl.setText("File : " + resourceUrl);
        tvLevels.setOnClickListener(view -> showMultiSelectList(getResources().getStringArray(R.array.array_levels), levels, view));
        tvSubjects.setOnClickListener(view -> showMultiSelectList(getResources().getStringArray(R.array.array_subjects), subjects, view));
        tvAddedBy.setText(userModel.getName());
        findViewById(R.id.btn_submit).setOnClickListener(view -> {
            saveResource();
        });
        findViewById(R.id.btn_cancel).setOnClickListener(view -> {
            finish();
        });
    }

    private void saveResource() {
        String title = etTitle.getText().toString();
        String author = etAuthor.getText().toString();
        String addedBy = tvAddedBy.getText().toString();
        String year = etYear.getText().toString();
        String description = etDescription.getText().toString();
        String publisher = etPublisher.getText().toString();
        String linkToLicense = etLinkToLicense.getText().toString();
        String openWhich = etOpenWhich.getText().toString();
        String lang = spnLang.getSelectedItem().toString();
        String media = spnMedia.getSelectedItem().toString();
        String resourceType = spnResourceType.getSelectedItem().toString();
        String openWith = spnOpenWith.getSelectedItem().toString();
        if (title.isEmpty()) {
            ((TextInputLayout) findViewById(R.id.tl_title)).setError("Title is required");
            return;
        }
        if (levels.isEmpty()) {
            ((TextInputLayout) findViewById(R.id.tl_title)).setError("Level is required");
            return;
        }
        if (subjects.isEmpty()) {
            ((TextInputLayout) findViewById(R.id.tl_title)).setError("Subject is required");
            return;
        }
        mRealm.executeTransactionAsync(realm -> {
            RealmMyLibrary resource = realm.createObject(RealmMyLibrary.class, UUID.randomUUID().toString());
            resource.setAddedBy(addedBy);
            resource.setTitle(title);
            resource.setAuthor(author);
            resource.setYear(year);
            resource.setDescription(description);
            resource.setPublisher(publisher);
            resource.setLinkToLicense(linkToLicense);
            resource.setOpenWith(openWith);
            resource.setLanguage(lang);
            resource.setMediaType(media);
            resource.setMediaType(resourceType);
            resource.setSubject(subjects);
            resource.setLevel(levels);
            resource.setResourceFor(resourceFor);
            resource.setResourceLocalAddress(resourceUrl);
            resource.setResourceOffline(true);
            resource.setFilename(resourceUrl.substring(resourceUrl.lastIndexOf("/")));
        });

    }

    private void showMultiSelectList(String[] list, List<String> items, View view) {
        CheckboxListView listView = new CheckboxListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.rowlayout, R.id.checkBoxRowLayout, list);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(adapter);
        new AlertDialog.Builder(this).setView(listView).setPositiveButton("Ok", (dialogInterface, i) -> {
            ArrayList<Integer> selected = listView.getSelectedItemsList();
            items.clear();
            String selection = "";
            for (Integer index : selected) {
                String s = list[index];
                selection += s + " ,";
                items.add(s);
            }
            ((TextView) view).setText(selection);

        }).setNegativeButton("Dismiss", null).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
