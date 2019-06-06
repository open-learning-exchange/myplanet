package org.ole.planet.myplanet.ui.library;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;

import io.realm.Realm;

public class AddResourceActivity extends AppCompatActivity {

    EditText etTitle, etAuthor, etYear, etDescription, etPublisher, etLinkToLicense, etOpenWhich;
    Spinner spnLang, spnMedia, spnResourceType, spnOpenWith;
    TextView tvSubjects, tvLevels, tvResourceFor;
    Realm mRealm;
    RealmUserModel userModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_resource);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        initializeViews();
        userModel = new UserProfileDbHandler(this).getUserModel();
        mRealm = new DatabaseService(this).getRealmInstance();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRealm!=null && !mRealm.isClosed())
            mRealm.close();
    }

    private void initializeViews() {
        etTitle = findViewById(R.id.et_title);
        etAuthor = findViewById(R.id.et_author);
        etYear = findViewById(R.id.et_year);
        etDescription = findViewById(R.id.et_description);
        etPublisher = findViewById(R.id.et_publisher);
        etLinkToLicense = findViewById(R.id.et_link_to_license);
        etOpenWhich= findViewById(R.id.et_open_which);
        spnLang= findViewById(R.id.spn_lang);
        spnMedia= findViewById(R.id.spn_media);
        spnResourceType= findViewById(R.id.spn_resource_type);
        spnOpenWith= findViewById(R.id.spn_open_with);
        tvSubjects= findViewById(R.id.tv_subject);
        tvLevels= findViewById(R.id.tv_levels);
        tvResourceFor= findViewById(R.id.tv_resource_for);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            finish();
        return super.onOptionsItemSelected(item);
    }
}
