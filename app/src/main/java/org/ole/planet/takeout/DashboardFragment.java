package org.ole.planet.takeout;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import org.lightcouch.CouchDbProperties;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myLibrary;
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

    //ImageButtons
    private ImageButton myLibraryImage;
    private ImageButton myCourseImage;
    private ImageButton myMeetUpsImage;
    private ImageButton myTeamsImage;

    //TextViews
    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;
    TextView txtFullName, txtCurDate, txtVisits;
    String fullName;
    Realm mRealm;

    public DashboardFragment() {
        //init dashboard
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        declareElements(view);
        imageButtonOnClickListeners();
        fullName = settings.getString("firstName", "") + " " + settings.getString("middleName", "") + " " + settings.getString("lastName", "");
        txtFullName.setText(fullName);
        txtCurDate.setText(currentDate());
        return view;
    }
  
    public void imageButtonOnClickListeners() {
        myLibraryImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageButtonAction("Clicked myLibrary");
            }
        });

        myCourseImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageButtonAction("Clicked myLibrary");
            }
        });

        myMeetUpsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageButtonAction("Clicked myLibrary");
            }
        });

        myTeamsImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                imageButtonAction("Clicked myTeams");
            }
        });
    }

    public void imageButtonAction(String btnmessage) {
        Log.e("DF: ", btnmessage);
        Intent intent = new Intent(getActivity(), PDFReaderActivity.class);
        startActivity(intent);
    }

    private void declareElements(View view) {
        // Imagebuttons
        myLibraryImage = (ImageButton) view.findViewById(R.id.myLibraryImageButton);
        myCourseImage = (ImageButton) view.findViewById(R.id.myCoursesImageButton);
        myMeetUpsImage = (ImageButton) view.findViewById(R.id.myMeetUpsImageButton);
        myTeamsImage = (ImageButton) view.findViewById(R.id.myTeamsImageButton);
        txtFullName = view.findViewById(R.id.txtFullName);
        txtCurDate = view.findViewById(R.id.txtCurDate);
        txtVisits = view.findViewById(R.id.txtVisits);
        realmConfig();
        myLibraryDiv(view);
    }

    private String currentDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dateformat = new SimpleDateFormat("dd-MMM-yyyy");
        String datetime = dateformat.format(c.getTime());
        return datetime;
    }

    public int offlineVisits() {
        //realmConfig("offlineActivities");
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

    public void realmConfig() {
        Realm.init(getContext());
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(Realm.DEFAULT_REALM_NAME)
                .deleteRealmIfMigrationNeeded()
                .schemaVersion(4)
                .build();
        Realm.setDefaultConfiguration(config);
        mRealm = Realm.getInstance(config);
    }



    public void myLibraryDiv(View view) {
        FlexboxLayout flexboxLayout = view.findViewById(R.id.flexboxLayout);
        flexboxLayout.setFlexDirection(FlexDirection.ROW);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                250,
                100
        );

        RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class).findAll();
        TextView[] textViewArray = new TextView[db_myLibrary.size()];
        int itemCnt = 0;
        for (realm_myLibrary items : db_myLibrary) {
            textViewArray[itemCnt] = new TextView(getContext());
            textViewArray[itemCnt].setPadding(20, 10, 20, 10);
            textViewArray[itemCnt].setBackgroundResource(R.drawable.dark_rect);
            textViewArray[itemCnt].setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textViewArray[itemCnt].setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
            textViewArray[itemCnt].setText(items.getTitle());
            textViewArray[itemCnt].setTextColor(getResources().getColor(R.color.dialog_sync_labels));
            if ((itemCnt % 2) == 0) {
                textViewArray[itemCnt].setBackgroundResource(R.drawable.light_rect);
            }
            flexboxLayout.addView(textViewArray[itemCnt], params);
            itemCnt++;
        }
    }



}
