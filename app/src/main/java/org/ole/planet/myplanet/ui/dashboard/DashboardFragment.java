package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
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

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;
import org.ole.planet.myplanet.ui.mymeetup.MyMeetupDetailFragment;
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment;
import org.ole.planet.myplanet.ui.team.MyTeamsDetailFragment;
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment;
import org.ole.planet.myplanet.ui.userprofile.UserProfileFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;

/**
 * A placeholder fragment containing a simple view.
 */
public class DashboardFragment extends BaseContainerFragment {

    public static final String PREFS_NAME = "OLE_PLANET";
    TextView txtFullName, txtVisits, tv_surveys, tv_submission, tv_achievement, txtRole;
    String fullName;
    Realm mRealm;
    DatabaseService dbService;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            250,
            100
    );
    private UserProfileDbHandler profileDbHandler;


    public DashboardFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view;
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("bell_theme", false)) {
            view = inflater.inflate(R.layout.fragment_home_bell, container, false);
        } else {
            view = inflater.inflate(R.layout.fragment_home, container, false);
        }
        profileDbHandler = new UserProfileDbHandler(getActivity());
        declareElements(view);
        fullName = profileDbHandler.getUserModel().getFullName();
        txtFullName.setText(fullName);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(Utilities.currentDate());
        RealmUserModel model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        ImageView imageView = view.findViewById(R.id.imageView);
        Utilities.loadImage(model.getUserImage(), imageView);
        txtVisits.setText(profileDbHandler.getOfflineVisits() + " visits");
        txtRole.setText(model.getRoleAsString());
        int noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser(settings.getString("userId", "--"), mRealm);
        (view.findViewById(R.id.img_survey_warn)).setVisibility(noOfSurvey == 0 ? View.VISIBLE : View.GONE);

        return view;
    }

    private void declareElements(View view) {
        txtFullName = view.findViewById(R.id.txtFullName);
        txtVisits = view.findViewById(R.id.txtVisits);
        txtRole = view.findViewById(R.id.txtRole);
        tv_surveys = view.findViewById(R.id.tv_surveys);
        tv_submission = view.findViewById(R.id.tv_submission);
        tv_achievement = view.findViewById(R.id.tv_achievement);
        tv_surveys.setOnClickListener(view12 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("survey")));
        tv_submission.setOnClickListener(view1 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("exam")));
        tv_achievement.setVisibility(Constants.showBetaFeature(Constants.KEY_ACHIEVEMENT, getActivity()) ? View.VISIBLE : View.GONE);
        tv_achievement.setOnClickListener(v -> homeItemClickListener.openCallFragment(new AchievementFragment()));
        view.findViewById(R.id.ll_user).setOnClickListener(view13 -> homeItemClickListener.openCallFragment(new UserProfileFragment()));
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        myLibraryDiv(view);
        initializeFlexBoxView(view, R.id.flexboxLayoutCourse, RealmMyCourse.class);
        initializeFlexBoxView(view, R.id.flexboxLayoutTeams, RealmMyTeam.class);
        initializeFlexBoxView(view, R.id.flexboxLayoutMeetups, RealmMeetup.class);
        showDownloadDialog(getLibraryList(mRealm));

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
            v.setBackgroundColor(getResources().getColor((itemCnt % 2) == 0 ? R.color.md_white_1000 : Constants.COLOR_MAP.get(RealmMyLibrary.class)));

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
        if (c == RealmMyCourse.class) {
            db_myCourses = RealmMyCourse.getMyByUserId(mRealm, settings);
        } else {
            db_myCourses = mRealm.where(c)
                    .contains("userId", settings.getString("userId", "--"), Case.INSENSITIVE).findAll();
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

    private void setTextColor(TextView textView, int itemCnt, Class c) {
        if ((itemCnt % 2) == 0) {
            textView.setBackgroundResource(R.drawable.light_rect);
            textView.setTextColor(getResources().getColor(R.color.md_black_1000));
        } else {
            textView.setBackgroundColor(getResources().getColor(Constants.COLOR_MAP.get(c)));
            textView.setTextColor(getResources().getColor(R.color.md_white_1000));
        }
    }


    public void setTextViewProperties(TextView[] textViewArray, int itemCnt, final RealmObject obj, Class c) {
        textViewArray[itemCnt] = new TextView(getContext());
        textViewArray[itemCnt].setPadding(20, 10, 20, 10);
        textViewArray[itemCnt].setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textViewArray[itemCnt].setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        if (obj instanceof RealmMyLibrary) {
            textViewArray[itemCnt].setText(((RealmMyLibrary) obj).getTitle());
        } else if (obj instanceof RealmMyCourse) {
            handleClick(((RealmMyCourse) obj).getCourseId(), ((RealmMyCourse) obj).getCourseTitle(), new TakeCourseFragment(), textViewArray[itemCnt]);
        } else if (obj instanceof RealmMyTeam) {
            //    textViewArray[itemCnt].setText(((RealmMyTeam) obj).getName());
            handleClick(((RealmMyTeam) obj).getTeamId(), ((RealmMyTeam) obj).getName(), new MyTeamsDetailFragment(), textViewArray[itemCnt]);
        } else if (obj instanceof RealmMeetup) {
            handleClick(((RealmMeetup) obj).getMeetupId(), ((RealmMeetup) obj).getTitle(), new MyMeetupDetailFragment(), textViewArray[itemCnt]);
        }
    }

    private void handleClick(final String id, String title, final Fragment f, TextView v) {
        v.setText(title);
        v.setOnClickListener(view -> {
            if (homeItemClickListener != null) {
                Bundle b = new Bundle();
                b.putString("id", id);
                f.setArguments(b);
                homeItemClickListener.openCallFragment(f);
            }
        });
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
}
