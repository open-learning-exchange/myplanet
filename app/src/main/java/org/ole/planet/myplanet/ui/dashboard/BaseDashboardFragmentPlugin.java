package org.ole.planet.myplanet.ui.dashboard;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseContainerFragment;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.model.RealmMyTeam;
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

public class BaseDashboardFragmentPlugin extends BaseContainerFragment {
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

    public void handleClickMyLife(String title, String imageId, LinearLayout linearLayout) {
        ((ImageView) linearLayout.getChildAt(0)).setImageResource(getResources().getIdentifier(imageId, "drawable", getActivity().getPackageName()));
        (((TextView) linearLayout.getChildAt(1))).setText(title);
        linearLayout.setOnClickListener(view -> {
            if (homeItemClickListener != null) {
                if (title.equals(getString(R.string.submission))) {
                    homeItemClickListener.openCallFragment(new MySubmissionFragment());
                } else if (title.equals(getString(R.string.news))) {
                    homeItemClickListener.openCallFragment(new NewsFragment());
                } else if (title.equals(getString(R.string.references))) {
                    homeItemClickListener.openCallFragment(new ReferenceFragment());
                } else if (title.equals(getString(R.string.calendar))) {
                    homeItemClickListener.openCallFragment(new CalendarFragment());
                } else if (title.equals(getString(R.string.achievements))) {
                    homeItemClickListener.openCallFragment(new AchievementFragment());
                } else if (title.equals(getString(R.string.mypersonals))) {
                    homeItemClickListener.openCallFragment(new MyPersonalsFragment());
                } else if (title.equals(getString(R.string.help_wanted))) {
                    homeItemClickListener.openCallFragment(new HelpWantedFragment());
                } else if (title.equals(getString(R.string.myhealth)) && Constants.showBetaFeature(Constants.KEY_MYHEALTH, getActivity())) {
                    homeItemClickListener.openCallFragment(new MyHealthFragment());
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
        } else if (obj instanceof RealmMyTeam) {
            if (((RealmMyTeam) obj).getTeamType().equals("sync")) {
                textViewArray[itemCnt].setTypeface(null, Typeface.BOLD);
            }
            //    textViewArray[itemCnt].setText(((RealmMyTeam) obj).getName());
            handleClick(((RealmMyTeam) obj).getId(), ((RealmMyTeam) obj).getName(), new TeamDetailFragment(), textViewArray[itemCnt]);
        } else if (obj instanceof RealmMeetup) {
            handleClick(((RealmMeetup) obj).getMeetupId(), ((RealmMeetup) obj).getTitle(), new MyMeetupDetailFragment(), textViewArray[itemCnt]);
        } else if (obj instanceof RealmMyLife) {
            // handleClickMyLife(((RealmMyLife) obj).get_id(),((RealmMyLife) obj).getTitle(),((RealmMyLife) obj).getImageId(),li[itemCnt]);
        }
    }

    public void setTextColor(TextView textView, int itemCnt, Class c) {
        //  int color = getResources().getColor(PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("bell_theme", false) ? Constants.COLOR_MAP.get(c) : R.color.md_grey_400);
        textView.setTextColor(getResources().getColor(R.color.md_black_1000));
        if ((itemCnt % 2) == 0) {
            textView.setBackgroundResource(R.drawable.light_rect);
        } else {
            textView.setBackgroundColor(getResources().getColor(R.color.md_grey_300));
        }
    }

    public void setLinearLayoutProperties(LinearLayout[] linearLayoutArray, int itemCnt, final RealmObject obj, Class c) {
        linearLayoutArray[itemCnt] = new LinearLayout(getContext());
        linearLayoutArray[itemCnt].setOrientation(LinearLayout.VERTICAL);
        linearLayoutArray[itemCnt].setMinimumWidth(R.dimen.user_image_size);
        linearLayoutArray[itemCnt].setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.CENTER;
        linearLayoutArray[itemCnt].setLayoutParams(lp);

        TextView tv = new TextView(getContext());
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp_tv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp_tv.gravity = Gravity.CENTER;
        tv.setPadding(8, 8, 8, 8);

        ImageView imageView = new ImageView(getContext());
        LinearLayout.LayoutParams lp_iv = new LinearLayout.LayoutParams(36, 36);
        lp_iv.gravity = Gravity.CENTER;
        imageView.setLayoutParams(lp_iv);

        linearLayoutArray[itemCnt].addView(imageView);
        linearLayoutArray[itemCnt].addView(tv);

        tv.setTextColor(getResources().getColor(R.color.md_black_1000));
        if ((itemCnt % 2) == 0) {
            linearLayoutArray[itemCnt].setBackgroundResource(R.drawable.light_rect);
        } else {
            linearLayoutArray[itemCnt].setBackgroundColor(getResources().getColor(R.color.md_grey_300));
        }

        handleClickMyLife(((RealmMyLife) obj).getTitle(), ((RealmMyLife) obj).getImageId(), linearLayoutArray[itemCnt]);

    }

    public List<RealmMyLife> getMyLifeListBase(String userId) {
        List<RealmMyLife> myLifeList = new ArrayList<>();
        myLifeList.add(new RealmMyLife("ic_myhealth", userId, getString(R.string.myhealth)));
        myLifeList.add(new RealmMyLife("ic_messages", userId, getString(R.string.messeges)));
        myLifeList.add(new RealmMyLife("my_achievement", userId, getString(R.string.achievements)));
        myLifeList.add(new RealmMyLife("ic_submissions", userId, getString(R.string.submission)));
        myLifeList.add(new RealmMyLife("ic_news", userId, getString(R.string.news)));
        myLifeList.add(new RealmMyLife("ic_references", userId, getString(R.string.references)));
        myLifeList.add(new RealmMyLife("ic_help_wanted", userId, getString(R.string.help_wanted)));
        myLifeList.add(new RealmMyLife("ic_calendar", userId, getString(R.string.calendar)));
        myLifeList.add(new RealmMyLife("ic_contacts", userId, getString(R.string.contacts)));
        myLifeList.add(new RealmMyLife("ic_mypersonals", userId, getString(R.string.mypersonals)));
        return myLifeList;
    }
}
