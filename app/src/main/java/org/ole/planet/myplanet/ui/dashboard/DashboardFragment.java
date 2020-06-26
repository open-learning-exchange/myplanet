package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.ui.news.NewsFragment;
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment;
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.TimeUtils;

import io.realm.Realm;

/**
 * A placeholder fragment containing a simple view.
 */
public class DashboardFragment extends BaseDashboardFragment {

    public static final String PREFS_NAME = "OLE_PLANET";
    TextView txtFullName, txtVisits, tv_surveys, tv_submission, tv_achievement, txtRole, tv_news;
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
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        declareElements(view);
        onLoaded(view);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(TimeUtils.currentDate());
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        int noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser(settings.getString("userId", "--"), mRealm);

        (getView().findViewById(R.id.img_survey_warn)).setVisibility(noOfSurvey == 0 ? View.VISIBLE : View.GONE);
        getView().findViewById(R.id.add_resource).setOnClickListener(v -> {
            // startActivity(new Intent(getActivity(), AddResourceActivity.class));
            new AddResourceFragment().show(getChildFragmentManager(), "Add Resource");
        });
    }

    private void declareElements(View view) {
        tv_surveys = view.findViewById(R.id.tv_surveys);
        tv_submission = view.findViewById(R.id.tv_submission);
        tv_achievement = view.findViewById(R.id.tv_achievement);
        tv_news = view.findViewById(R.id.tv_news);
        tv_surveys.setOnClickListener(view12 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("survey")));
        tv_news.setOnClickListener(view12 -> homeItemClickListener.openCallFragment(new NewsFragment()));
        tv_submission.setOnClickListener(view1 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("exam")));
        tv_achievement.setVisibility(Constants.showBetaFeature(Constants.KEY_ACHIEVEMENT, getActivity()) ? View.VISIBLE : View.GONE);
        tv_achievement.setOnClickListener(v -> homeItemClickListener.openCallFragment(new AchievementFragment()));
//        view.findViewById(R.id.ll_user).setOnClickListener(view13 -> homeItemClickListener.openCallFragment(new UserProfileFragment()));
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        initView(view);

    }

}
