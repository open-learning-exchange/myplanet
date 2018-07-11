package org.ole.planet.takeout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import org.ole.planet.takeout.Data.Download;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_offlineActivities;
import org.ole.planet.takeout.datamanager.MyDownloadService;
import org.ole.planet.takeout.utilities.DialogUtils;
import org.ole.planet.takeout.utilities.Utilities;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.takeout.Dashboard.MESSAGE_PROGRESS;

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
        fullName = settings.getString("firstName", "") + " " + settings.getString("middleName", "") + " " + settings.getString("lastName", "");
        txtFullName.setText(fullName);
        txtCurDate.setText(currentDate());
        registerReceiver();
        return view;
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
        showDownloadDialog();
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
        TextView[] myLibraryTextViewArray = new TextView[db_myLibrary.size()];
        int itemCnt = 0;
        for (final realm_myLibrary items : db_myLibrary) {
            setTextViewProperties(myLibraryTextViewArray, itemCnt, items);
            myLibraryItemClickAction(myLibraryTextViewArray[itemCnt], items);
            if ((itemCnt % 2) == 0) {
                myLibraryTextViewArray[itemCnt].setBackgroundResource(R.drawable.light_rect);
            }
            flexboxLayout.addView(myLibraryTextViewArray[itemCnt], params);
            itemCnt++;
        }
    }

    private void showDownloadDialog() {
        final RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class).equalTo("resourceOffline", false).findAll();
        DialogUtils.getDowloadDialog(getActivity()).items(getListAsArray(db_myLibrary))
                .itemsCallbackMultiChoice(null, new MaterialDialog.ListCallbackMultiChoice() {
                    @Override
                    public boolean onSelection(MaterialDialog dialog, Integer[] which, CharSequence[] text) {
                        dialog.setSelectedIndices(which);
                        return true;
                    }
                }).onPositive(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                downloadFiles(db_myLibrary, dialog.getSelectedIndices());
            }
        }).onNeutral(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                downloadAllFiles(db_myLibrary);
            }
        });
        DialogUtils.getDowloadDialog(getActivity()).show();
    }

    private void downloadAllFiles(RealmResults<realm_myLibrary> db_myLibrary) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < db_myLibrary.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(i), settings));
        }
        Utilities.openDownloadService(getActivity(), urls);

    }

    private void downloadFiles(RealmResults<realm_myLibrary> db_myLibrary, Integer[] selectedItems) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < selectedItems.length; i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(selectedItems[i]), settings));
        }
        Utilities.openDownloadService(getActivity(), urls);
    }


    private void registerReceiver() {
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MESSAGE_PROGRESS);
        bManager.registerReceiver(broadcastReceiver, intentFilter);

    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(MESSAGE_PROGRESS)) {

                Download download = intent.getParcelableExtra("download");
                Utilities.log("Progress " + download.getProgress());
                // TODO: 7/11/18 Show Progress in UI
            }
        }
    };


    private CharSequence[] getListAsArray(RealmResults<realm_myLibrary> db_myLibrary) {
        CharSequence[] array = new CharSequence[db_myLibrary.size()];
        for (int i = 0; i < db_myLibrary.size(); i++) {
            array[i] = db_myLibrary.get(i).getTitle();
        }
        return array;
    }

    public void myLibraryItemClickAction(TextView textView, final realm_myLibrary items) {
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (items.getResourceOffline()) {
                    Log.e("Item", items.getId() + " Resource is Offline " + items.getResourceRemoteAddress());
                } else {
                    Log.e("Item", items.getId() + " Resource is Online " + items.getResourceRemoteAddress());
                }
            }
        });
    }

    public void setTextViewProperties(TextView[] textViewArray, int itemCnt, realm_myLibrary items) {
        textViewArray[itemCnt] = new TextView(getContext());
        textViewArray[itemCnt].setPadding(20, 10, 20, 10);
        textViewArray[itemCnt].setBackgroundResource(R.drawable.dark_rect);
        textViewArray[itemCnt].setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textViewArray[itemCnt].setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        textViewArray[itemCnt].setText(items.getTitle());
        textViewArray[itemCnt].setTextColor(getResources().getColor(R.color.dialog_sync_labels));
    }

}
