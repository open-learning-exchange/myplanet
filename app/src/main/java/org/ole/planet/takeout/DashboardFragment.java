package org.ole.planet.takeout;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import org.ole.planet.takeout.Data.realm_UserModel;
import org.ole.planet.takeout.Data.realm_meetups;
import org.ole.planet.takeout.Data.realm_myCourses;
import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.Data.realm_myTeams;
import org.ole.planet.takeout.base.BaseContainerFragment;
import org.ole.planet.takeout.courses.TakeCourseFragment;
import org.ole.planet.takeout.datamanager.DatabaseService;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;

/**
 * A placeholder fragment containing a simple view.
 */
public class DashboardFragment extends BaseContainerFragment {

    //TextViews
    public static final String PREFS_NAME = "OLE_PLANET";
    private static String auth = ""; // Main Auth Session Token for any Online File Streaming/ Viewing -- Constantly Updating Every 15 mins
    public String globalFilePath = Environment.getExternalStorageDirectory() + File.separator + "ole" + File.separator;
    TextView txtFullName, txtCurDate, txtVisits;
    ImageView userImage;
    String fullName;
    Realm mRealm;
    DatabaseService dbService;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            250,
            100
    );
    //ImageButtons
    private ImageButton myLibraryImage;
    private ImageButton myCourseImage;
    private ImageButton myMeetUpsImage;
    private ImageButton myTeamsImage;
    private UserProfileDbHandler profileDbHandler;


    public DashboardFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        profileDbHandler = new UserProfileDbHandler(getActivity());
        //  settings = getActivity().getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE);
        declareElements(view);
        fullName = settings.getString("firstName", "") + " " + settings.getString("middleName", "") + " " + settings.getString("lastName", "");
        txtFullName.setText(fullName);
        txtCurDate.setText(Utilities.currentDate());
        realm_UserModel model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        ImageView imageView = view.findViewById(R.id.imageView);
        Utilities.loadImage(model.getUserImage(), imageView);
        txtVisits.setText(profileDbHandler.getOfflineVisits() + " visits");
        return view;
    }

    private void declareElements(View view) {
        myLibraryImage = (ImageButton) view.findViewById(R.id.myLibraryImageButton);
        myCourseImage = (ImageButton) view.findViewById(R.id.myCoursesImageButton);
        myMeetUpsImage = (ImageButton) view.findViewById(R.id.myMeetUpsImageButton);
        myTeamsImage = (ImageButton) view.findViewById(R.id.myTeamsImageButton);
        txtFullName = view.findViewById(R.id.txtFullName);
        txtCurDate = view.findViewById(R.id.txtCurDate);
        txtVisits = view.findViewById(R.id.txtVisits);
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        myLibraryDiv(view);
        initializeFlexBoxView(view, R.id.flexboxLayoutCourse, realm_myCourses.class);
        initializeFlexBoxView(view, R.id.flexboxLayoutTeams, realm_myTeams.class);
        initializeFlexBoxView(view, R.id.flexboxLayoutMeetups, realm_meetups.class);
        showDownloadDialog(getLibraryList());
        AuthSessionUpdater.timerSendPostNewAuthSessionID(settings);
    }

    public void myLibraryDiv(View view) {
        FlexboxLayout flexboxLayout = view.findViewById(R.id.flexboxLayout);
        flexboxLayout.setFlexDirection(FlexDirection.ROW);
        RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class).isNotEmpty("userId").findAll();
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

    public void initializeFlexBoxView(View v, int id, Class c) {
        FlexboxLayout flexboxLayout = v.findViewById(id);
        flexboxLayout.setFlexDirection(FlexDirection.ROW);
        setUpMyList(c, flexboxLayout);
    }

    public void setUpMyList(Class c, FlexboxLayout flexboxLayout) {
        RealmResults<RealmObject> db_myCourses = mRealm.where(c).findAll();
        TextView[] myCoursesTextViewArray = new TextView[db_myCourses.size()];
        int itemCnt = 0;
        for (final RealmObject items : db_myCourses) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, items);
            if ((itemCnt % 2) == 0) {
                myCoursesTextViewArray[itemCnt].setBackgroundResource(R.drawable.light_rect);
            }
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params);
            itemCnt++;
        }
    }


    public void setTextViewProperties(TextView[] textViewArray, int itemCnt, final RealmObject obj) {
        textViewArray[itemCnt] = new TextView(getContext());
        textViewArray[itemCnt].setPadding(20, 10, 20, 10);
        textViewArray[itemCnt].setBackgroundResource(R.drawable.dark_rect);
        textViewArray[itemCnt].setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textViewArray[itemCnt].setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        textViewArray[itemCnt].setTextColor(getResources().getColor(R.color.dialog_sync_labels));
        if (obj instanceof realm_myLibrary) {
            textViewArray[itemCnt].setText(((realm_myLibrary) obj).getTitle());
        } else if (obj instanceof realm_myCourses) {
            textViewArray[itemCnt].setText(((realm_myCourses) obj).getCourseTitle());
            textViewArray[itemCnt].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleCourseClick((realm_myCourses) obj);
                }
            });
        } else if (obj instanceof realm_myTeams) {
            textViewArray[itemCnt].setText(((realm_myTeams) obj).getName());
        } else if (obj instanceof realm_meetups) {
            textViewArray[itemCnt].setText(((realm_meetups) obj).getTitle());
        }
    }

    private void handleCourseClick(realm_myCourses obj) {
        if (homeItemClickListener != null) {
            TakeCourseFragment t = new TakeCourseFragment();
            Bundle b = new Bundle();
            b.putString("courseId", ((realm_myCourses) obj).getCourseId());
            t.setArguments(b);
            homeItemClickListener.openCallFragment(t);
        }
    }

    private RealmResults<realm_myLibrary> getLibraryList() {
        return mRealm.where(realm_myLibrary.class)
                .equalTo("resourceOffline", false)
                .isNotEmpty("userId")
                .or()
                .equalTo("resourceOffline", false)
                .isNotEmpty("courseId")
                .findAll();
    }


    public void myLibraryItemClickAction(TextView textView, final realm_myLibrary items) {
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (items.getResourceOffline()) {
                    profileDbHandler.setResourceOpenCount(items.getResourceLocalAddress());
                    Log.e("Item", items.getId() + " Resource is Offline " + items.getResourceRemoteAddress());
                    openFileType(items, "offline");
                } else {
                    Log.e("Item", items.getId() + " Resource is Online " + items.getResourceRemoteAddress());
                    openFileType(items, "online");
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        profileDbHandler.onDestory();
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
