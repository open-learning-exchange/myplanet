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
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.CheckboxListView;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

public class AddResourceActivity extends AppCompatActivity {

    EditText etTitle, etAuthor, etYear, etDescription, etPublisher, etLinkToLicense, etOpenWhich;
    Spinner spnLang, spnMedia, spnResourceType, spnOpenWith;
    EditText tvSubjects, tvLevels, tvResourceFor;
    Realm mRealm;
    RealmUserModel userModel;
    List<String> subjects;
    List<String> levels;
    List<String> resourceFor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_resource);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        levels = new ArrayList<>();
        subjects = new ArrayList<>();
        resourceFor = new ArrayList<>();
        initializeViews();
        userModel = new UserProfileDbHandler(this).getUserModel();
        mRealm = new DatabaseService(this).getRealmInstance();
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
        tvLevels = findViewById(R.id.tv_levels);
        tvResourceFor = findViewById(R.id.tv_resource_for);
        tvLevels.setOnClickListener(view -> showMultiSelectList(getResources().getStringArray(R.array.array_levels), levels, view));
        tvSubjects.setOnClickListener(view -> showMultiSelectList(getResources().getStringArray(R.array.array_subjects), subjects, view));
        findViewById(R.id.btn_submit).setOnClickListener(view -> {
            String title = etTitle.getText().toString();
            String author = etAuthor.getText().toString();
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
        });
        findViewById(R.id.btn_cancel).setOnClickListener(view -> {
            finish();
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
