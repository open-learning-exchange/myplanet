package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.ui.calendar.CalendarFragment;
import org.ole.planet.myplanet.ui.course.CourseFragment;
import org.ole.planet.myplanet.ui.library.LibraryFragment;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.ui.mylife.LifeFragment;
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment;
import org.ole.planet.myplanet.ui.news.NewsFragment;
import org.ole.planet.myplanet.ui.references.ReferenceFragment;
import org.ole.planet.myplanet.ui.team.TeamFragment;
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment;
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.Date;

/**
 * A placeholder fragment containing a simple view.
 */
public class BellDashboardFragment extends BaseDashboardFragment {

    public static final String PREFS_NAME = "OLE_PLANET";
    TextView tvCommunityName, tvDate;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home_bell, container, false);
        declareElements(view);
        onLoaded(view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvDate.setText(TimeUtils.formatDate(new Date().getTime()));
        tvCommunityName.setText(model.getPlanetCode());
        ((DashboardActivity) getActivity()).getSupportActionBar().hide();
        getView().findViewById(R.id.add_resource).setOnClickListener(v -> {
            new AddResourceFragment().show(getChildFragmentManager(), "Add Resource");
        });
    }

    private void declareElements(View view) {
        tvDate = view.findViewById(R.id.txt_date);
        tvCommunityName = view.findViewById(R.id.txt_community_name);
//        view.findViewById(R.id.iv_setting).setOnClickListener(V->{
//            startActivity(new Intent(getActivity(), SettingActivity.class));
//        });
        initView(view);
       // view.findViewById(R.id.ll_achievement).setOnClickListener(v -> homeItemClickListener.openCallFragment(new AchievementFragment()));
       // view.findViewById(R.id.ll_news).setOnClickListener(v -> homeItemClickListener.openCallFragment(new NewsFragment()));
        View.OnClickListener showToast = view1 -> Toast.makeText(getContext(), "Feature Not Available", Toast.LENGTH_LONG).show();
        view.findViewById(R.id.ll_home_team).setOnClickListener(v->homeItemClickListener.openCallFragment(new TeamFragment()));
//        view.findViewById(R.id.ll_messages).setOnClickListener(showToast);
       // view.findViewById(R.id.ll_calendar).setOnClickListener(v -> homeItemClickListener.openCallFragment(new CalendarFragment()));
       // view.findViewById(R.id.ll_contacts).setOnClickListener(showToast);
    //    view.findViewById(R.id.ll_references).setOnClickListener(view12 -> homeItemClickListener.openCallFragment(new ReferenceFragment()));
       // view.findViewById(R.id.ll_help_wanted).setOnClickListener(showToast);
        view.findViewById(R.id.myLibraryImageButton).setOnClickListener(v -> openHelperFragment(new LibraryFragment()));
        view.findViewById(R.id.myCoursesImageButton).setOnClickListener(v -> openHelperFragment(new CourseFragment()));

       // view.findViewById(R.id.ll_mySubmissions).setOnClickListener(v -> homeItemClickListener.openCallFragment(new MySubmissionFragment()));
       // view.findViewById(R.id.ll_myHealth).setOnClickListener(showToast);
        view.findViewById(R.id.myLifeImageButton).setOnClickListener(v -> homeItemClickListener.openCallFragment(new LifeFragment()));

     //   view.findViewById(R.id.ll_myPersonals).setOnClickListener(v->homeItemClickListener.openCallFragment(new MyPersonalsFragment()));
    }

    private void openHelperFragment(Fragment f) {
        Fragment temp = f;
        Bundle b = new Bundle();
        b.putBoolean("isMyCourseLib", true);
        temp.setArguments(b);
        homeItemClickListener.openCallFragment(temp);

    }

}
