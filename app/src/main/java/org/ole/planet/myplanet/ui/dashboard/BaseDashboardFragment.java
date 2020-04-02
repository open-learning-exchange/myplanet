package org.ole.planet.myplanet.ui.dashboard;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayout;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.NotificationCallback;
import org.ole.planet.myplanet.callback.SyncListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.datamanager.MyDownloadService;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmTeamNotification;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.TransactionSyncManager;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.dashboard.notification.NotificationFragment;
import org.ole.planet.myplanet.ui.team.TeamDetailFragment;
import org.ole.planet.myplanet.ui.userprofile.UserProfileFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.FileUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;

public class BaseDashboardFragment extends BaseDashboardFragmentPlugin implements NotificationCallback {
    public UserProfileDbHandler profileDbHandler;
    String fullName;
    Realm mRealm;
    TextView txtFullName, txtVisits, txtRole;
    DatabaseService dbService;
    RealmUserModel model;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(250, 100);

    void onLoaded(View v) {
        txtFullName = v.findViewById(R.id.txtFullName);
        txtVisits = v.findViewById(R.id.txtVisits);
        txtRole = v.findViewById(R.id.txtRole);
        profileDbHandler = new UserProfileDbHandler(getActivity());
        model = profileDbHandler.getUserModel();
        fullName = profileDbHandler.getUserModel().getFullName();
        ImageView imageView = v.findViewById(R.id.imageView);
        Utilities.loadImage(model.getUserImage(), imageView);
        txtVisits.setText(profileDbHandler.getOfflineVisits() + " visits");
        txtRole.setText(" - " + model.getRoleAsString());
        txtFullName.setText(fullName);
    }

    public void forceDownloadNewsImages() {
        if (mRealm == null)
            mRealm = new DatabaseService(getActivity()).getRealmInstance();
        Utilities.toast(getActivity(), "Please select starting date : ");
        Calendar now = Calendar.getInstance();
        DatePickerDialog dpd = new DatePickerDialog(getActivity(), (datePicker, i, i1, i2) -> {
            now.set(Calendar.YEAR, i);
            now.set(Calendar.MONTH, i1);
            now.set(Calendar.DAY_OF_MONTH, i2);
            List<RealmMyLibrary> imageList = mRealm.where(RealmMyLibrary.class).equalTo("isPrivate", true).greaterThan("createdDate", now.getTimeInMillis()).equalTo("mediaType", "image").findAll();
            ArrayList<String> urls = new ArrayList<>();
            getUrlsAndStartDownload(imageList, settings, urls);

        }, now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH));
        dpd.setTitle("Read offline news from : ");
        dpd.show();
    }

    @Override
    public void downloadDictionary() {
        ArrayList<String> list = new ArrayList<>();
        list.add(Constants.DICTIONARY_URL);
        if (!FileUtils.checkFileExist(Constants.DICTIONARY_URL)) {
            Utilities.toast(getActivity(), "Downloading started, please check notification...");
            Utilities.openDownloadService(getActivity(), list);
        } else {
            Utilities.toast(getActivity(), "File already exists...");
        }
    }

    public void myLibraryDiv(View view) {
        TextView count = view.findViewById(R.id.count_library);
        FlexboxLayout flexboxLayout = view.findViewById(R.id.flexboxLayout);
        flexboxLayout.setFlexDirection(FlexDirection.ROW);
        List<RealmMyLibrary> db_myLibrary = RealmMyLibrary.getMyLibraryByUserId(mRealm, settings);
        if (db_myLibrary.size() == 0) {
            count.setVisibility(View.GONE);
        } else {
            count.setText(db_myLibrary.size() + "");
        }
        int itemCnt = 0;
        for (final RealmMyLibrary items : db_myLibrary) {
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.item_library_home, null);
            setTextColor((v.findViewById(R.id.title)), itemCnt, RealmMyLibrary.class);
            v.setBackgroundColor(getResources().getColor((itemCnt % 2) == 0 ? R.color.md_white_1000 : R.color.md_grey_300));
            ((TextView) v.findViewById(R.id.title)).setText(items.getTitle());
            (v.findViewById(R.id.detail)).setOnClickListener(vi -> {
                if (homeItemClickListener != null)
                    homeItemClickListener.openLibraryDetailFragment(items);
            });
            myLibraryItemClickAction(v.findViewById(R.id.title), items);
            flexboxLayout.addView(v, params);
            itemCnt++;
        }
    }

    public void initializeFlexBoxView(View v, int id, Class c) {
        FlexboxLayout flexboxLayout = v.findViewById(id);
        flexboxLayout.setFlexDirection(FlexDirection.ROW);
        setUpMyList(c, flexboxLayout, v);
    }

    public void setUpMyList(Class c, FlexboxLayout flexboxLayout, View view) {
        List<RealmObject> db_myCourses;
        String userId = settings.getString("userId", "--");
        setUpMyLife(userId);
        if (c == RealmMyCourse.class) {
            db_myCourses = RealmMyCourse.getMyByUserId(mRealm, settings);
        } else if (c == RealmMyTeam.class) {
            int i = myTeamInit(flexboxLayout);
            setCountText(i, RealmMyTeam.class, view);
            return;
        } else if (c == RealmMyLife.class) {
            myLifeListInit(flexboxLayout);
            return;
        } else {
            db_myCourses = mRealm.where(c).contains("userId", userId, Case.INSENSITIVE).findAll();
        }
        setCountText(db_myCourses.size(), c, view);
        TextView[] myCoursesTextViewArray = new TextView[db_myCourses.size()];
        int itemCnt = 0;
        for (final RealmObject items : db_myCourses) {
            setTextViewProperties(myCoursesTextViewArray, itemCnt, items, c);
            setTextColor(myCoursesTextViewArray[itemCnt], itemCnt, c);
            flexboxLayout.addView(myCoursesTextViewArray[itemCnt], params);
            itemCnt++;
        }
    }

    private int myTeamInit(FlexboxLayout flexboxLayout) {
        List<RealmObject> dbMyTeam = RealmMyTeam.getMyTeamsByUserId(mRealm, settings);
        String userId = new UserProfileDbHandler(getActivity()).getUserModel().getId();
        int count = 0;
        for (RealmObject ob : dbMyTeam) {
            View v = LayoutInflater.from(getActivity()).inflate(R.layout.item_home_my_team, flexboxLayout, false);
            TextView name = v.findViewById(R.id.tv_name);
            setBackgroundColor(v, count);
            if (((RealmMyTeam) ob).getTeamType().equals("sync")) {
                name.setTypeface(null, Typeface.BOLD);
            }
            handleClick(((RealmMyTeam) ob).getId(), ((RealmMyTeam) ob).getName(), new TeamDetailFragment(), name);
            showNotificationIcons(ob, v, userId);
            name.setText(((RealmMyTeam) ob).getName());
            flexboxLayout.addView(v, params);
            count++;
        }
        return dbMyTeam.size();
    }

    private void showNotificationIcons(RealmObject ob, View v, String userId) {
        long current = Calendar.getInstance().getTimeInMillis();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        ImageView imgTask = v.findViewById(R.id.img_task);
        ImageView imgChat = v.findViewById(R.id.img_chat);
        RealmTeamNotification notification = mRealm.where(RealmTeamNotification.class)
                .equalTo("parentId", ((RealmMyTeam) ob).getId())
                .equalTo("type", "chat")
                .findFirst();
        long chatCount = mRealm.where(RealmNews.class).equalTo("viewableBy", "teams").equalTo("viewableId", ((RealmMyTeam) ob).getId()).count();
        if (notification != null) {
            imgChat.setVisibility(notification.getLastCount() < chatCount ? View.VISIBLE : View.GONE);
        }
        List<RealmTeamTask> tasks = mRealm.where(RealmTeamTask.class).equalTo("teamId", ((RealmMyTeam) ob).getId()).equalTo("completed", false).equalTo("assignee", userId)
                .between("deadline", current, tomorrow.getTimeInMillis()).findAll();
        imgTask.setVisibility(tasks.size() > 0 ? View.VISIBLE : View.GONE);
    }

    private void myLifeListInit(FlexboxLayout flexboxLayout) {
        List<RealmMyLife> db_myLife, raw_myLife;
        raw_myLife = RealmMyLife.getMyLifeByUserId(mRealm, settings);
        db_myLife = new ArrayList<>();
        for (RealmMyLife item : raw_myLife) if (item.isVisible()) db_myLife.add(item);
        int itemCnt = 0;
        for (final RealmObject items : db_myLife) {
            flexboxLayout.addView(getLayout(itemCnt, items), params);
            itemCnt++;
        }
    }

    private void setUpMyLife(String userId) {
        Realm realm = new DatabaseService(getContext()).getRealmInstance();
        List<RealmMyLife> realmObjects = RealmMyLife.getMyLifeByUserId(mRealm, settings);
        if (realmObjects.isEmpty()) {
            if (!realm.isInTransaction()) realm.beginTransaction();
            List<RealmMyLife> myLifeListBase = getMyLifeListBase(userId);
            RealmMyLife ml;
            int weight = 1;
            for (RealmMyLife item : myLifeListBase) {
                ml = realm.createObject(RealmMyLife.class, UUID.randomUUID().toString());
                ml.setTitle(item.getTitle());
                ml.setImageId(item.getImageId());
                ml.setWeight(weight);
                ml.setUserId(item.getUserId());
                ml.setVisible(true);
                weight++;
            }
            realm.commitTransaction();
        }

    }

    public void myLibraryItemClickAction(TextView textView, final RealmMyLibrary items) {
        textView.setOnClickListener(v -> openResource(items));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        profileDbHandler.onDestory();
    }

    public void setCountText(int countText, Class c, View v) {
        if (c == RealmMyCourse.class) {
            TextView tv_count_course = v.findViewById(R.id.count_course);
            updateCountText(countText, tv_count_course);
        } else if (c == RealmMeetup.class) {
            TextView tv_count_meetup = v.findViewById(R.id.count_meetup);
            updateCountText(countText, tv_count_meetup);
        } else if (c == RealmMyTeam.class) {
            TextView tv_count_team = v.findViewById(R.id.count_team);
            updateCountText(countText, tv_count_team);
        }
    }

    public void updateCountText(int countText, TextView tv) {
        tv.setText(countText + "");
        hideCountIfZero(tv, countText);
    }

    public void hideCountIfZero(View v, int count) {
        v.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }


    public void initView(View view) {
        view.findViewById(R.id.imageView).setOnClickListener(view13 -> homeItemClickListener.openCallFragment(new UserProfileFragment()));
        view.findViewById(R.id.txtFullName).setOnClickListener(view13 -> homeItemClickListener.openCallFragment(new UserProfileFragment()));
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        myLibraryDiv(view);
        initializeFlexBoxView(view, R.id.flexboxLayoutCourse, RealmMyCourse.class);
        initializeFlexBoxView(view, R.id.flexboxLayoutTeams, RealmMyTeam.class);
        initializeFlexBoxView(view, R.id.flexboxLayoutMeetups, RealmMeetup.class);
        initializeFlexBoxView(view, R.id.flexboxLayoutMyLife, RealmMyLife.class);
        showNotificationFragment();
    }

    public void showNotificationFragment() {
        NotificationFragment fragment = new NotificationFragment();
        fragment.callback = this;
        fragment.resourceList = getLibraryList(mRealm);
        fragment.show(getChildFragmentManager(), "");
    }

    @Override
    public void showResourceDownloadDialog() {
        showDownloadDialog(getLibraryList(mRealm));
    }

    @Override
    public void syncKeyId() {
        ProgressDialog di = new ProgressDialog(getActivity());
        di.setMessage("Syncing health , please wait...");
        TransactionSyncManager.syncKeyIv(mRealm, settings, new SyncListener() {
            @Override
            public void onSyncStarted() {
                di.show();
            }

            @Override
            public void onSyncComplete() {
                di.dismiss();
                Utilities.toast(getActivity(), "myHealth synced successfully");
            }

            @Override
            public void onSyncFailed(String msg) {
                di.dismiss();
                Utilities.toast(getActivity(), "myHealth synced failed");
            }
        });
    }
}
