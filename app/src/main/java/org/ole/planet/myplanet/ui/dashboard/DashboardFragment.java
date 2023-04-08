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
import org.ole.planet.myplanet.databinding.CardProfileBinding;
import org.ole.planet.myplanet.databinding.FragmentHomeBinding;
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
    private FragmentHomeBinding binding;
    CardProfileBinding cardProfileBinding;
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        cardProfileBinding = CardProfileBinding.bind(cardProfileBinding.getRoot());
        declareElements(binding.getRoot());
        onLoaded();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(TimeUtils.currentDate());
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        int noOfSurvey = RealmSubmission.getNoOfSurveySubmissionByUser(settings.getString("userId", "--"), mRealm);

        cardProfileBinding.imgSurveyWarn.setVisibility(noOfSurvey == 0 ? View.VISIBLE : View.GONE);
        binding.addResource.setOnClickListener(v -> {
            // startActivity(new Intent(getActivity(), AddResourceActivity.class));
            new AddResourceFragment().show(getChildFragmentManager(), "Add Resource");
        });
    }

    private void declareElements(View view) {
        cardProfileBinding.tvSurveys.setOnClickListener(view12 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("survey")));
        cardProfileBinding.tvNews.setOnClickListener(view12 -> homeItemClickListener.openCallFragment(new NewsFragment()));
        cardProfileBinding.tvSubmission.setOnClickListener(view1 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("exam")));
        cardProfileBinding.tvAchievement.setVisibility(Constants.showBetaFeature(Constants.KEY_ACHIEVEMENT, getActivity()) ? View.VISIBLE : View.GONE);
        cardProfileBinding.tvAchievement.setOnClickListener(v -> homeItemClickListener.openCallFragment(new AchievementFragment()));
//        binding.llUser.setOnClickListener(view13 -> homeItemClickListener.openCallFragment(new UserProfileFragment()));
        dbService = new DatabaseService(getActivity());
        mRealm = dbService.getRealmInstance();
        initView(view);

    }
}
