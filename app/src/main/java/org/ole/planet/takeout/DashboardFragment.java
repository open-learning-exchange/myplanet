package org.ole.planet.takeout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import org.lightcouch.CouchDbProperties;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_offlineActivities;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

import static android.content.Context.MODE_PRIVATE;

/**
 * A placeholder fragment containing a simple view.
 */
public class DashboardFragment extends Fragment {
    private ImageButton myLibraryImage;
    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;
    TextView txtFullName, txtCurDate, txtVisits;
    String fullName;
    Realm mRealm;
    CouchDbProperties properties;

    public DashboardFragment() {
        //init dashboard
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        declareElements(view);
        fullName = settings.getString("firstName", "") + " " + settings.getString("middleName", "") + " " + settings.getString("lastName", "");
        txtFullName.setText(fullName);
        txtCurDate.setText(currentDate());
        //txtVisits.setText(offlineVisits());

        myLibraryImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.e("DF: ", "Clicked myLibrary");
                Intent intent = new Intent(getActivity(), PDFReaderActivity.class);
                startActivity(intent);
            }
        });

        return view;
    }

    private void declareElements(View view) {
        // Imagebuttons
        myLibraryImage = view.findViewById(R.id.myLibrary);
        txtFullName = view.findViewById(R.id.txtFullName);
        txtCurDate = view.findViewById(R.id.txtCurDate);
        txtVisits = view.findViewById(R.id.txtVisits);
    }

    private String currentDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dateformat = new SimpleDateFormat("dd-MMM-yyyy");
        String datetime = dateformat.format(c.getTime());
        return datetime;
    }

    public int offlineVisits() {
        realmConfig("offlineActivities");
        realm_offlineActivities offlineActivities = mRealm.createObject(realm_offlineActivities.class, UUID.randomUUID().toString());
        offlineActivities.setUserId(settings.getString("name", ""));
        offlineActivities.setType("Login");
        offlineActivities.setDescription("Member login on offline application");
        offlineActivities.setUserFullName(fullName);
        RealmResults<realm_offlineActivities> db_users = mRealm.where(realm_offlineActivities.class)
                .equalTo("userId", settings.getString("name", ""))
                .equalTo("type", "Visits")
                .findAll();
        if (!db_users.isEmpty()) {
            return db_users.size();
        } else {
            return 0;
        }
    }

    public void realmConfig(String dbName) {
        Realm.init(getContext());
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        mRealm = Realm.getInstance(config);
        properties = new CouchDbProperties()
                .setDbName(dbName)
                .setCreateDbIfNotExist(false)
                .setProtocol(settings.getString("url_Scheme", "http"))
                .setHost(settings.getString("url_Host", "192.168.2.1"))
                .setPort(settings.getInt("url_Port", 3000))
                .setUsername(settings.getString("url_user", ""))
                .setPassword(settings.getString("url_pwd", ""))
                .setMaxConnections(100)
                .setConnectionTimeout(0);

    }

}
