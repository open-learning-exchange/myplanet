package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.FragmentHomeBinding;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.ui.library.AddResourceFragment;
import org.ole.planet.myplanet.ui.news.NewsFragment;
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment;
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.TimeUtils;

import io.realm.Realm;

public class DashboardFragment extends BaseDashboardFragment {
    private FragmentHomeBinding fragmentHomeBinding;
    public static final String PREFS_NAME = "OLE_PLANET";
    Realm mRealm;
    DatabaseService dbService;

    public DashboardFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false);
        View view = fragmentHomeBinding.getRoot();

        fragmentHomeBinding.cardProfile.tvSurveys.setOnClickListener(view12 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("survey")));
        fragmentHomeBinding.cardProfile.tvNews.setOnClickListener(view12 -> homeItemClickListener.openCallFragment(new NewsFragment()));
        fragmentHomeBinding.cardProfile.tvSubmission.setOnClickListener(view1 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("exam")));
        fragmentHomeBinding.cardProfile.tvAchievement.setVisibility(Constants.showBetaFeature(Constants.KEY_ACHIEVEMENT, getActivity()) ? View.VISIBLE : View.GONE);
        fragmentHomeBinding.cardProfile.tvAchievement.setOnClickListener(v -> homeItemClickListener.openCallFragment(new AchievementFragment()));
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        onLoaded(view);
        initView(view);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(TimeUtils.currentDate());
        return fragmentHomeBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        int noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser(settings.getString("userId", "--"), mRealm);

        fragmentHomeBinding.cardProfile.imgSurveyWarn.setVisibility(noOfSurvey == 0 ? View.VISIBLE : View.GONE);
        fragmentHomeBinding.addResource.setOnClickListener(v -> {
            new AddResourceFragment().show(getChildFragmentManager(),  getString(R.string.add_res));
        });
    }
}