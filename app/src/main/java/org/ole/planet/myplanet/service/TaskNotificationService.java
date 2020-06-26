package org.ole.planet.myplanet.service;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.Calendar;
import java.util.List;

import io.realm.Realm;

public class TaskNotificationService extends JobService {

    @Override
    public boolean onStartJob(JobParameters job) {
        Realm mRealm = new DatabaseService(this).getRealmInstance();
        long current = Calendar.getInstance().getTimeInMillis();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        RealmUserModel user = new UserProfileDbHandler(this).getUserModel();
        if (user != null) {
            List<RealmTeamTask> tasks = mRealm.where(RealmTeamTask.class).equalTo("completed", false).equalTo("assignee", user.getId()).equalTo("notified", false)
                    .between("deadline", current, tomorrow.getTimeInMillis()).findAll();
            mRealm.beginTransaction();
            for (RealmTeamTask in : tasks) {
                NotificationUtil.create(this, R.drawable.ole_logo, in.getTitle(), "Task expires on " + TimeUtils.formatDate(in.getDeadline()));
                in.setNotified(true);
            }
            mRealm.commitTransaction();
        }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return false;
    }
}
