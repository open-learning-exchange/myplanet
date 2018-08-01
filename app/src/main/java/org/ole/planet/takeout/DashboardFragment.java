package org.ole.planet.takeout;

import android.app.IntentService;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import org.json.JSONObject;
import org.ole.planet.takeout.Data.Download;
import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.ExoPlayerVideo;
import org.ole.planet.takeout.Data.realm_offlineActivities;
import org.ole.planet.takeout.callback.OnHomeItemClickListener;
import org.ole.planet.takeout.datamanager.ApiClient;
import org.ole.planet.takeout.datamanager.ApiInterface;
import org.ole.planet.takeout.datamanager.MyDownloadService;
import org.ole.planet.takeout.library.MyLibraryFragment;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.userprofile.UserProfileFragment;
import org.ole.planet.takeout.utilities.DialogUtils;
import org.ole.planet.takeout.utilities.Utilities;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmResults;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

import static android.content.Context.MODE_PRIVATE;
import static org.ole.planet.takeout.Dashboard.MESSAGE_PROGRESS;

/**
 * A placeholder fragment containing a simple view.
 */
public class DashboardFragment extends Fragment implements View.OnClickListener {

    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;
    TextView txtFullName, txtCurDate, txtVisits;
    ImageView userImage;
    String fullName;
    Realm mRealm;
    ProgressDialog prgDialog;
    UserProfileDbHandler profileDbHandler;
    ArrayList<Integer> selectedItemsList = new ArrayList<>();

    //ImageButtons
    private ImageButton myLibraryImage;
    private ImageButton myCourseImage;
    private ImageButton myMeetUpsImage;
    private ImageButton myTeamsImage;

    private Call<ResponseBody> request;

    private String auth = ""; // Main Auth Session Token for any Online File Streaming/ Viewing -- Constantly Updating Every 15 mins


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

    OnHomeItemClickListener listener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnHomeItemClickListener) {
            listener = (OnHomeItemClickListener) context;
        }
    }

    public DashboardFragment() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        profileDbHandler.onDestory();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        declareElements(view);
        fullName = Utilities.getFullName(settings);
        txtFullName.setText(fullName);
        txtCurDate.setText(Utilities.currentDate());
        profileDbHandler = new UserProfileDbHandler(getActivity());
        realm_UserModel model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        Utilities.log(model.getUserImage() + " image");
        ImageView imageView = view.findViewById(R.id.imageView);
        Utilities.loadImage(model.getUserImage(), imageView);
        txtVisits.setText(profileDbHandler.getOfflineVisits() + " visits");
        prgDialog = DialogUtils.getProgressDialog(getActivity());
        registerReceiver();
        myLibraryImage.setOnClickListener(this);
        myCourseImage.setOnClickListener(this);
        myMeetUpsImage.setOnClickListener(this);
        return view;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.myLibraryImageButton:
                openCallFragment(new MyLibraryFragment());
                break;
            case R.id.myCoursesImageButton:
                openCallFragment(new MyCourseFragment());
                break;
            case R.id.myMeetUpsImageButton:
                openCallFragment(new MyCourseFragment());
                break;
            default:
                openCallFragment(new DashboardFragment());
                break;
        }
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

        view.findViewById(R.id.ll_user).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    listener.openCallFragment(new UserProfileFragment());
            }
        });
        realmConfig();
        myLibraryDiv(view);
        myCoursesDiv(view);
        showDownloadDialog();
        timerSendPostNewAuthSessionID();
    }

    public void openCallFragment(Fragment newfragment) {
        FragmentTransaction fragmentTransaction = getActivity().getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, newfragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
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
        for (final realm_myLibrary items : db_myLibrary){
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
        for (final realm_myCourses items: db_myCourses) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, null ,items);
            if ((itemCnt % 2) == 0) {
                myCoursesTextViewArray[itemCnt].setBackgroundResource(R.drawable.light_rect);
            }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params);
            itemCnt++;
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
                    downloadFiles(db_myLibrary, selectedItemsList);

                }
            }).setNeutralButton(R.string.download_all, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    downloadAllFiles(db_myLibrary);
                }
            }).setNegativeButton(R.string.txt_cancel, null).show();
        }
    }

    private void downloadAllFiles(RealmResults<realm_myLibrary> db_myLibrary) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < db_myLibrary.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(i), settings));
        }
        startDownload(urls);
    }


    private void downloadFiles(RealmResults<realm_myLibrary> db_myLibrary, ArrayList<Integer> selectedItems) {
        ArrayList urls = new ArrayList();
        for (int i = 0; i < selectedItems.size(); i++) {
            urls.add(Utilities.getUrl(db_myLibrary.get(selectedItems.get(i)), settings));
            Log.e("URLS",""+urls);
        }
        startDownload(urls);
    }

    private void startDownload(ArrayList urls) {
        if (!urls.isEmpty()) {
            prgDialog.show();
            Utilities.openDownloadService(getActivity(), urls);
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
                    profileDbHandler.setResourceOpenCount(items.getResourceLocalAddress());
                    if(items.getMediaType().equals("video")){
                        playVideo("offline", items);
                    }
                } else {
                    Log.e("Item", items.getId() + " Resource is Online " + items.getResourceRemoteAddress());
                    if(items.getMediaType().equals("video")){
                        playVideo("online", items);
                    }
                }
            }
        });
    }

    public void setTextViewProperties(TextView[] textViewArray, int itemCnt, realm_myLibrary items, realm_myCourses itemsCourse) {

        textViewArray[itemCnt] = new TextView(getContext());
        textViewArray[itemCnt].setPadding(20, 10, 20, 10);
        textViewArray[itemCnt].setBackgroundResource(R.drawable.dark_rect);
        textViewArray[itemCnt].setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textViewArray[itemCnt].setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        textViewArray[itemCnt].setTextColor(getResources().getColor(R.color.dialog_sync_labels));
        if(items != null){
            textViewArray[itemCnt].setText(items.getTitle());
        }else if(itemsCourse != null){
            textViewArray[itemCnt].setText(itemsCourse.getCourse_rev());
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

    // Plays Video Using ExoPlayerVideo.java
    private void playVideo(String videoType, final realm_myLibrary items){

        Intent intent = new Intent(DashboardFragment.this.getActivity(), ExoPlayerVideo.class);
        Bundle bundle = new Bundle();
        bundle.putString("videoType", videoType);
        if(videoType.equals("online")){
            bundle.putString("videoURL",""+items.getResourceRemoteAddress());
            bundle.putString("Auth", auth);
        }else if(videoType.equals("offline")){
            bundle.putString("videoURL",""+ Uri.fromFile(new File(""+Utilities.getSDPathFromUrl(items.getResourceRemoteAddress()))));
            bundle.putString("Auth", "");
        }
        intent.putExtras(bundle);
        startActivity(intent);
    }

    // sendPost() - Meant to get New AuthSession Token for viewing Online resources such as Video, and basically any file.
    // It creates a session of about 20mins after which a new AuthSession Token will be needed.
    // During these 20mins items.getResourceRemoteAddress() will work in obtaining the files necessary.
    public void sendPost() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) getSessionUrl().openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept","application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(getJsonObject().toString());

                    os.flush();
                    os.close();

                    setAuthSession(conn.getHeaderFields());
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    public void setAuthSession(Map<String, List<String>> responseHeader){
        String headerauth[] = responseHeader.get("Set-Cookie").get(0).split(";");
        auth = headerauth[0];
    }

    // Updates Auth Session Token every 15 mins to prevent Timing Out
    public void timerSendPostNewAuthSessionID() {
        Timer timer = new Timer ();
        TimerTask hourlyTask = new TimerTask () {
            @Override
            public void run () {
                sendPost();
            }
        };
        timer.schedule(hourlyTask, 0, 1000*60*15);
    }

    public JSONObject getJsonObject(){
        try {
            JSONObject jsonParam = new JSONObject();
            jsonParam.put("name", settings.getString("url_user",""));
            jsonParam.put("password", settings.getString("url_pwd",""));
            return jsonParam;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
    public URL getSessionUrl(){
        try {
            String pref = settings.getString("serverURL", "");
            pref += "/_session";
            URL SERVER_URL = new URL(pref);
            return SERVER_URL;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
