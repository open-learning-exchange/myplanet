package org.ole.planet.takeout;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import org.ole.planet.takeout.Data.Download;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.utilities.DialogUtils;
import org.ole.planet.takeout.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.takeout.Dashboard.MESSAGE_PROGRESS;

/**
 * A placeholder fragment containing a simple view.
 */
public class DashboardFragment extends Fragment {

    //TextViews
    public static final String PREFS_NAME = "OLE_PLANET";
    public static SharedPreferences settings;
    static ProgressDialog prgDialog;
    private static String auth = ""; // Main Auth Session Token for any Online File Streaming/ Viewing -- Constantly Updating Every 15 mins
    public String globalFilePath = Environment.getExternalStorageDirectory() + File.separator + "ole" + File.separator;
    TextView txtFullName, txtCurDate, txtVisits;
    String fullName;
    Realm mRealm;
    ArrayList<Integer> selectedItemsList = new ArrayList<>();
    //ImageButtons
    private ImageButton myLibraryImage;
    private ImageButton myCourseImage;
    private ImageButton myMeetUpsImage;
    private ImageButton myTeamsImage;
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MESSAGE_PROGRESS) && prgDialog != null) {
                Download download = intent.getParcelableExtra("download");
                if (!download.isFailed()) {
                    setProgress(download);
                } else {
                    DialogUtils.showError(prgDialog, "Download Failed");
                }
            }
        }
    };

    public DashboardFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        declareElements(view);
        fullName = settings.getString("firstName", "") + " " + settings.getString("middleName", "") + " " + settings.getString("lastName", "");
        txtFullName.setText(fullName);
        txtCurDate.setText(Utilities.currentDate());
        prgDialog = DialogUtils.getProgressDialog(getActivity());
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
        myCoursesDiv(view);
        showDownloadDialog();
        AuthSessionUpdater.timerSendPostNewAuthSessionID(settings);
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
            setTextViewProperties(myLibraryTextViewArray, itemCnt, items, null);
            myLibraryItemClickAction(myLibraryTextViewArray[itemCnt], items);
            if ((itemCnt % 2) == 0) {
                myLibraryTextViewArray[itemCnt].setBackgroundResource(R.drawable.light_rect);
            }
            flexboxLayout.addView(myLibraryTextViewArray[itemCnt], params);
            itemCnt++;
        }
    }

    public void myCoursesDiv(View view) {
        FlexboxLayout flexboxLayout = view.findViewById(R.id.flexboxLayoutCourse);
        flexboxLayout.setFlexDirection(FlexDirection.ROW);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                250,
                100
        );
        RealmResults<realm_myCourses> db_myCourses = mRealm.where(realm_myCourses.class).findAll();
        TextView[] myCoursesTextViewArray = new TextView[db_myCourses.size()];
        int itemCnt = 0;
        for (final realm_myCourses items : db_myCourses) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, null, items);
            if ((itemCnt % 2) == 0) {
                myCoursesTextViewArray[itemCnt].setBackgroundResource(R.drawable.light_rect);
            }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params);
            itemCnt++;
        }
    }

    public void setTextViewProperties(TextView[] textViewArray, int itemCnt, realm_myLibrary items, realm_myCourses itemsCourse) {
        textViewArray[itemCnt] = new TextView(getContext());
        textViewArray[itemCnt].setPadding(20, 10, 20, 10);
        textViewArray[itemCnt].setBackgroundResource(R.drawable.dark_rect);
        textViewArray[itemCnt].setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textViewArray[itemCnt].setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        textViewArray[itemCnt].setTextColor(getResources().getColor(R.color.dialog_sync_labels));
        if (items != null) {
            textViewArray[itemCnt].setText(items.getTitle());
        } else if (itemsCourse != null) {
            textViewArray[itemCnt].setText(itemsCourse.getCourseTitle());
        }
    }

    public void startDownload(ArrayList urls) {
        if (!urls.isEmpty()) {
            prgDialog.show();
            Utilities.openDownloadService(getActivity(), urls);
        }
    }

    private void showDownloadDialog() {
        final RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class).equalTo("resourceOffline", false).findAll();
        if (!db_myLibrary.isEmpty()) {
            new AlertDialog.Builder(getActivity()).setTitle(R.string.download_suggestion).setMultiChoiceItems(realm_myLibrary.getListAsArray(db_myLibrary), null, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i, boolean b) {
                    DialogUtils.handleCheck(selectedItemsList, b, i);
                }
            }).setPositiveButton(R.string.download_selected, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    startDownload(DownloadFiles.downloadFiles(db_myLibrary, selectedItemsList, settings));
                }
            }).setNeutralButton(R.string.download_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    startDownload(DownloadFiles.downloadAllFiles(db_myLibrary, settings));
                }
            }).setNegativeButton(R.string.txt_cancel, null).show();
        }
    }

    public void setProgress(Download download) {
        prgDialog.setProgress(download.getProgress());
        if (!TextUtils.isEmpty(download.getFileName())) {
            prgDialog.setTitle(download.getFileName());
        }
        if (download.isCompleteAll()) {
            DialogUtils.showError(prgDialog, "All files downloaded successfully");
        }
    }

    private void registerReceiver() {
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MESSAGE_PROGRESS);
        bManager.registerReceiver(broadcastReceiver, intentFilter);
    }

    public void myLibraryItemClickAction(TextView textView, final realm_myLibrary items) {
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (items.getResourceOffline()) {
                    Log.e("Item", items.getId() + " Resource is Offline " + items.getResourceRemoteAddress());
                    openFileType(items, "offline");
                } else {
                    Log.e("Item", items.getId() + " Resource is Online " + items.getResourceRemoteAddress());
                    openFileType(items, "online");
                }
            }
        });
    }

    public void openFileType(final realm_myLibrary items, String videotype) {
        if (items.getMediaType().equals("video")) {
            playVideo(videotype, items);
        } else {
            checkFileExtension(items);
        }
    }

    //Sets Auth Session Variable every 15 mins
    public void setAuthSession(Map<String, List<String>> responseHeader) {
        String headerauth[] = responseHeader.get("Set-Cookie").get(0).split(";");
        auth = headerauth[0];
    }

    // Plays Video Using ExoPlayerVideo.java
    public void playVideo(String videoType, final realm_myLibrary items) {
        Intent intent = new Intent(DashboardFragment.this.getActivity(), ExoPlayerVideo.class);
        Bundle bundle = new Bundle();
        bundle.putString("videoType", videoType);
        if (videoType.equals("online")) {
            bundle.putString("videoURL", "" + items.getResourceRemoteAddress());
            Log.e("AUTH", "" + auth);
            bundle.putString("Auth", "" + auth);
        } else if (videoType.equals("offline")) {
            bundle.putString("videoURL", "" + Uri.fromFile(new File("" + Utilities.getSDPathFromUrl(items.getResourceRemoteAddress()))));
            bundle.putString("Auth", "");
        }
        intent.putExtras(bundle);
        startActivity(intent);
    }

    public void openIntent(realm_myLibrary items, Class typeClass) {
        Intent fileOpenIntent = new Intent(DashboardFragment.this.getActivity(), typeClass);
        fileOpenIntent.putExtra("TOUCHED_FILE", items.getResourceLocalAddress());
        startActivity(fileOpenIntent);
    }

    public void checkFileExtension(realm_myLibrary items) {
        String filenameArray[] = items.getResourceLocalAddress().split("\\.");
        String extension = filenameArray[filenameArray.length - 1];

        switch (extension) {
            case "pdf":
                openIntent(items, PDFReaderActivity.class);
                break;
            case "bmp":
            case "gif":
            case "jpg":
            case "png":
            case "webp":
                openIntent(items, ImageViewerActivity.class);
                break;
            default:
                checkMoreFileExtensions(extension, items);
                break;
        }
    }

    public void checkMoreFileExtensions(String extension, realm_myLibrary items) {
        switch (extension) {
            case "txt":
                openIntent(items, TextFileViewerActivity.class);
                break;
            case "md":
                openIntent(items, MarkdownViewerActivity.class);
                break;
            case "csv":
                openIntent(items, CSVViewerActivity.class);
                break;
            default:
                Toast.makeText(DashboardFragment.this.getContext(), "This file type is currently unsupported", Toast.LENGTH_LONG).show();
                break;
        }

    }
}
