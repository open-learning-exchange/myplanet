package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.calendar.CalendarFragment;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;
import org.ole.planet.myplanet.ui.helpwanted.HelpWantedFragment;
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment;
import org.ole.planet.myplanet.ui.myhealth.MyHealthFragment;
import org.ole.planet.myplanet.ui.mymeetup.MyMeetupDetailFragment;
import org.ole.planet.myplanet.ui.news.NewsFragment;
import org.ole.planet.myplanet.ui.references.ReferenceFragment;
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment;
import org.ole.planet.myplanet.ui.team.TeamDetailFragment;
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.RealmObject;

public class BaseDashboardFragmentPlugin extends BaseContainerFragment  {
    public void handleClick(final String id, String title, final Fragment f, TextView v) {
        v.setText(title);
        v.setOnClickListener(view -> {
            if (homeItemClickListener != null) {
                Bundle b = new Bundle();
                b.putString("id", id);
                if (f instanceof TeamDetailFragment)
                    b.putBoolean("isMyTeam", true);
                f.setArguments(b);
                homeItemClickListener.openCallFragment(f);
            }
        });
    }

    public void handleClickMyLife(String title, View v) {
        v.setOnClickListener(view -> {
            if (homeItemClickListener != null) {
                if (title.equals(getString(R.string.submission))) {
                    homeItemClickListener.openCallFragment(new MySubmissionFragment());
                } else if (title.equals(getString(R.string.news))) {
                    homeItemClickListener.openCallFragment(new NewsFragment());
                } else if (title.equals(getString(R.string.references))) {
                    homeItemClickListener.openCallFragment(new ReferenceFragment());
                } else if (title.equals(getString(R.string.calendar))) {
                    homeItemClickListener.openCallFragment(new CalendarFragment());
                } else if (title.equals(getString(R.string.my_survey))) {
                    homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("survey"));
                } else if (title.equals(getString(R.string.achievements))) {
                    homeItemClickListener.openCallFragment(new AchievementFragment());
                } else if (title.equals(getString(R.string.mypersonals))) {
                    homeItemClickListener.openCallFragment(new MyPersonalsFragment());
                } else if (title.equals(getString(R.string.help_wanted))) {
                    homeItemClickListener.openCallFragment(new HelpWantedFragment());
                } else if (title.equals(getString(R.string.myhealth)) && Constants.showBetaFeature(Constants.KEY_MYHEALTH, getActivity())) {
                    if (!model.getId().startsWith("guest")) {
                        homeItemClickListener.openCallFragment(new MyHealthFragment());
                    }else{
                        Utilities.toast(getActivity(), "Feature not available for guest user");
                    }
                } else {
                    Utilities.toast(getActivity(), "Feature Not Available");
                }
            }
        });
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
        }else if (obj instanceof RealmMeetup) {
            handleClick(((RealmMeetup) obj).getMeetupId(), ((RealmMeetup) obj).getTitle(), new MyMeetupDetailFragment(), textViewArray[itemCnt]);
        }
    }

    public void setTextColor(TextView textView, int itemCnt, Class c) {
        textView.setTextColor(getResources().getColor(R.color.md_black_1000));
        setBackgroundColor(textView, itemCnt);
    }

    public View getLayout(int itemCnt, final RealmObject obj) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.item_my_life, null);
        ImageView img = v.findViewById(R.id.img);
        TextView counter = v.findViewById(R.id.tv_count);
        TextView name = v.findViewById(R.id.tv_name);
        setBackgroundColor(v, itemCnt);
        String title = ((RealmMyLife) obj).getTitle();

        img.setImageResource(getResources().getIdentifier(((RealmMyLife) obj).getImageId(), "drawable", getActivity().getPackageName()));
        name.setText(title);
        RealmUserModel user= new UserProfileDbHandler(getActivity()).getUserModel();
        if (title.equals(getString(R.string.my_survey))) {
            counter.setVisibility(View.VISIBLE);
            int noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser( user.getId(), mRealm);
            counter.setText(noOfSurvey +"");
            Utilities.log("Count " + noOfSurvey);
        } else {
            counter.setVisibility(View.GONE);
        }
        handleClickMyLife(title, v);
        return v;
    }

    public List<RealmMyLife> getMyLifeListBase(String userId) {
        List<RealmMyLife> myLifeList = new ArrayList<>();
        myLifeList.add(new RealmMyLife("ic_myhealth", userId, getString(R.string.myhealth)));
        myLifeList.add(new RealmMyLife("ic_messages", userId, getString(R.string.messeges)));
        myLifeList.add(new RealmMyLife("my_achievement", userId, getString(R.string.achievements)));
        myLifeList.add(new RealmMyLife("ic_submissions", userId, getString(R.string.submission)));
        myLifeList.add(new RealmMyLife("ic_my_survey", userId, getString(R.string.my_survey)));
//        myLifeList.add(new RealmMyLife("ic_news", userId, getString(R.string.news)));
        myLifeList.add(new RealmMyLife("ic_references", userId, getString(R.string.references)));
        myLifeList.add(new RealmMyLife("ic_help_wanted", userId, getString(R.string.help_wanted)));
        myLifeList.add(new RealmMyLife("ic_calendar", userId, getString(R.string.calendar)));
        myLifeList.add(new RealmMyLife("ic_contacts", userId, getString(R.string.contacts)));
        myLifeList.add(new RealmMyLife("ic_mypersonals", userId, getString(R.string.mypersonals)));
        return myLifeList;
    }

    public void setBackgroundColor(View v, int count) {
        if ((count % 2) == 0) {
            v.setBackgroundResource(R.drawable.light_rect);
        } else {
            v.setBackgroundColor(getResources().getColor(R.color.md_grey_300));
        }
    }
}
