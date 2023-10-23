package org.ole.planet.myplanet.ui.dashboard;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.CardProfileBinding;
import org.ole.planet.myplanet.databinding.FragmentFinanceBinding;
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

public class DashboardFragment extends BaseDashboardFragment {
    private FragmentHomeBinding fragmentHomeBinding;
    private CardProfileBinding cardProfileBinding;
    public static final String PREFS_NAME = "OLE_PLANET";
    TextView txtFullName, txtVisits, tv_surveys, tv_submission, tv_achievement, txtRole, tv_news;
    Realm mRealm;
    DatabaseService dbService;
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(250, 100);
    private UserProfileDbHandler profileDbHandler;

    public DashboardFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false);
        View view = fragmentHomeBinding.getRoot();
        cardProfileBinding = CardProfileBinding.inflate(getLayoutInflater());
        View cardProfileView = cardProfileBinding.getRoot();

//        declareElements(cardProfileView);
        cardProfileView.findViewById(R.id.tv_surveys).setOnClickListener(view12 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("survey")));
        cardProfileView.findViewById(R.id.tv_news).setOnClickListener(view12 -> homeItemClickListener.openCallFragment(new NewsFragment()));
        cardProfileView.findViewById(R.id.tv_submission).setOnClickListener(view1 -> homeItemClickListener.openCallFragment(MySubmissionFragment.newInstance("exam")));
        cardProfileView.findViewById(R.id.tv_achievement).setVisibility(Constants.showBetaFeature(Constants.KEY_ACHIEVEMENT, getActivity()) ? View.VISIBLE : View.GONE);
        cardProfileView.findViewById(R.id.tv_achievement).setOnClickListener(v -> homeItemClickListener.openCallFragment(new AchievementFragment()));
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

        cardProfileBinding.imgSurveyWarn.setVisibility(noOfSurvey == 0 ? View.VISIBLE : View.GONE);
        fragmentHomeBinding.addResource.setOnClickListener(v -> {
            new AddResourceFragment().show(getChildFragmentManager(),  getString(R.string.add_res));
        });
    }

//    private void declareElements(View view) {
//        tv_surveys = view.findViewById(R.id.tv_surveys);
//        tv_submission = view.findViewById(R.id.tv_submission);
//        tv_achievement = view.findViewById(R.id.tv_achievement);
//        tv_news = view.findViewById(R.id.tv_news);

//        initView(view);
//    }
}
