package org.ole.planet.myplanet;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import org.apache.commons.lang3.BooleanUtils;
import org.ole.planet.myplanet.Data.realm_UserModel;
import org.ole.planet.myplanet.Data.realm_meetups;
import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.Data.realm_myTeams;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.courses.TakeCourseFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.mymeetup.MyMeetupDetailFragment;
import org.ole.planet.myplanet.teams.MyTeamsDetailFragment;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

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

    public static final String PREFS_NAME = "OLE_PLANET";
    private static String auth = ""; // Main Auth Session Token for any Online File Streaming/ Viewing -- Constantly Updating Every 15 mins
    public String globalFilePath = Environment.getExternalStorageDirectory() + File.separator + "ole" + File.separator;
    TextView txtFullName, txtVisits;
    String fullName;
    Realm mRealm;
    DatabaseService dbService;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            250,
            100
    );
    private ImageView myLibraryImage;
    private ImageView myCourseImage;
    private ImageView myMeetUpsImage;
    private ImageView myTeamsImage;
    private UserProfileDbHandler profileDbHandler;


    public DashboardFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        profileDbHandler = new UserProfileDbHandler(getActivity());
        declareElements(view);
        fullName = settings.getString("firstName", "") + " " + settings.getString("middleName", "") + " " + settings.getString("lastName", "");
        txtFullName.setText(fullName);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(Utilities.currentDate());
        realm_UserModel model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        ImageView imageView = view.findViewById(R.id.imageView);
        Utilities.loadImage(model.getUserImage(), imageView);
        txtVisits.setText(profileDbHandler.getOfflineVisits() + " visits");
        return view;
    }

    private void declareElements(View view) {
        myLibraryImage = view.findViewById(R.id.myLibraryImageButton);
        myCourseImage = view.findViewById(R.id.myCoursesImageButton);
        myMeetUpsImage = view.findViewById(R.id.myMeetUpsImageButton);
        myTeamsImage = view.findViewById(R.id.myTeamsImageButton);
        txtFullName = view.findViewById(R.id.txtFullName);
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
        TextView count = view.findViewById(R.id.count_library);
        FlexboxLayout flexboxLayout = view.findViewById(R.id.flexboxLayout);
        flexboxLayout.setFlexDirection(FlexDirection.ROW);
        RealmResults<realm_myLibrary> db_myLibrary = mRealm.where(realm_myLibrary.class).isNotEmpty("userId").findAll();
        count.setText(db_myLibrary.size() + "");
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
        setUpMyList(c, flexboxLayout, v);
    }

    public void setUpMyList(Class c, FlexboxLayout flexboxLayout, View view) {
        RealmResults<RealmObject> db_myCourses = mRealm.where(c).isNotEmpty("userId").findAll();
        setCountText(db_myCourses.size(), c, view);
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
            handleClick(((realm_myCourses) obj).getCourseId(), ((realm_myCourses) obj).getCourseTitle(), new TakeCourseFragment(), textViewArray[itemCnt]);
        } else if (obj instanceof realm_myTeams) {
            //    textViewArray[itemCnt].setText(((realm_myTeams) obj).getName());
            handleClick(((realm_myTeams) obj).getTeamId(), ((realm_myTeams) obj).getName(), new MyTeamsDetailFragment(), textViewArray[itemCnt]);

        } else if (obj instanceof realm_meetups) {
            handleClick(((realm_meetups) obj).getMeetupId(), ((realm_meetups) obj).getTitle(), new MyMeetupDetailFragment(), textViewArray[itemCnt]);
        }
    }

    private void handleClick(final String id, String title, final Fragment f, TextView v) {
        v.setText(title);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (homeItemClickListener != null) {
                    Bundle b = new Bundle();
                    b.putString("id", id);
                    f.setArguments(b);
                    homeItemClickListener.openCallFragment(f);
                }
            }
        });
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
                if (items.getResourceOffline() != null && items.getResourceOffline()) {
                    profileDbHandler.setResourceOpenCount(items.getResourceLocalAddress());
                    openFileType(items, "offline");
                } else if (TextUtils.equals(items.getMediaType(), "video")) {
                    openFileType(items, "online");
                } else {
                    Utilities.toast(getActivity(), "Resource can not be opened please download the resource first.");
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


    public void setCountText(int countText, Class c, View v) {
        if (c == realm_myCourses.class)
            ((TextView) v.findViewById(R.id.count_course)).setText(countText + "");
        else if (c == realm_meetups.class)
            ((TextView) v.findViewById(R.id.count_meetup)).setText(countText + "");
        else if (c == realm_myTeams.class)
            ((TextView) v.findViewById(R.id.count_team)).setText(countText + "");
    }
}
