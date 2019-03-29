package org.ole.planet.myplanet.ui.dashboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
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
import org.ole.planet.myplanet.ui.SettingActivity;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;
import org.ole.planet.myplanet.ui.mymeetup.MyMeetupDetailFragment;
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment;
import org.ole.planet.myplanet.ui.team.MyTeamsDetailFragment;
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment;
import org.ole.planet.myplanet.ui.userprofile.UserProfileFragment;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;

/**
 * A placeholder fragment containing a simple view.
 */
public class BellDashboardFragment extends BaseDashboardFragment  {

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
        tvDate.setText(Utilities.formatDate(new Date().getTime()));
        tvCommunityName.setText(model.getPlanetCode());
        ((DashboardActivity) getActivity()).getSupportActionBar().hide();
    }

    private void declareElements(View view) {
        tvDate = view.findViewById(R.id.txt_date);
        tvCommunityName = view.findViewById(R.id.txt_community_name);
//        view.findViewById(R.id.iv_setting).setOnClickListener(V->{
//            startActivity(new Intent(getActivity(), SettingActivity.class));
//        });
        initView(view);
    }

}
