package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.ui.course.CourseFragment;
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.ui.library.LibraryFragment;
import org.ole.planet.myplanet.ui.mylife.LifeFragment;
import org.ole.planet.myplanet.ui.survey.SurveyFragment;
import org.ole.planet.myplanet.ui.team.TeamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import java.util.Date;


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
        initView(view);
        view.findViewById(R.id.ll_home_team).setOnClickListener(v -> homeItemClickListener.openCallFragment(new TeamFragment()));
        view.findViewById(R.id.myLibraryImageButton).setOnClickListener(v -> openHelperFragment(new LibraryFragment()));
        view.findViewById(R.id.myCoursesImageButton).setOnClickListener(v -> openHelperFragment(new CourseFragment()));
        view.findViewById(R.id.fab_survey).setOnClickListener(v -> openHelperFragment(new SurveyFragment()));
        view.findViewById(R.id.fab_feedback).setOnClickListener(v -> openHelperFragment(new FeedbackListFragment()));
        view.findViewById(R.id.fab_feedback).setOnClickListener(v -> openHelperFragment(new FeedbackListFragment()));
        view.findViewById(R.id.myLifeImageButton).setOnClickListener(v -> homeItemClickListener.openCallFragment(new LifeFragment()));
    }

    private void openHelperFragment(Fragment f) {
        Fragment temp = f;
        Bundle b = new Bundle();
        b.putBoolean("isMyCourseLib", true);
        temp.setArguments(b);
        homeItemClickListener.openCallFragment(temp);

    }

}
