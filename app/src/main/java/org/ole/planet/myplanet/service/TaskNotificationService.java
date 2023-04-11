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
        
        //Added a check for the user variable being null, and returning early if it is.
        if (user == null) {
            return false;
        }
        List<RealmTeamTask> tasks = mRealm.where(RealmTeamTask.class)
                .equalTo("completed", false)
                .equalTo("assignee", user.getId())
                .equalTo("notified", false)
                .between("deadline", current, tomorrow.getTimeInMillis())
                .findAll();
         // Replaced beginTransaction() and commitTransaction() with a lambda expression that calls executeTransaction().
         // This is a more concise and recommended way to perform transactions in Realm.      
        mRealm.executeTransaction(r -> {
            //Renamed the loop variable from in to task for better readability.
            //Moved the loop body inside the transaction block.
            for (RealmTeamTask task : tasks) {
                NotificationUtil.create(this, R.drawable.ole_logo, task.getTitle(),
                        "Task expires on " + TimeUtils.formatDate(task.getDeadline()));
                task.setNotified(true);
            }
        });
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return false;
    }
}
