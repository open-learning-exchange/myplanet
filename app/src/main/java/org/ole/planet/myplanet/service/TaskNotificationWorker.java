package org.ole.planet.myplanet.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.NotificationUtil;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.Calendar;
import java.util.List;

import io.realm.Realm;

public class TaskNotificationWorker extends Worker {
    private Context context;

    public TaskNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Realm mRealm = new DatabaseService(context).getRealmInstance();
        long current = Calendar.getInstance().getTimeInMillis();
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        RealmUserModel user = new UserProfileDbHandler(context).getUserModel();
        if (user != null) {
            List<RealmTeamTask> tasks = mRealm.where(RealmTeamTask.class)
                    .equalTo("completed", false)
                    .equalTo("assignee", user.getId())
                    .equalTo("notified", false)
                    .between("deadline", current, tomorrow.getTimeInMillis())
                    .findAll();

            mRealm.beginTransaction();
            for (RealmTeamTask in : tasks) {
                NotificationUtil.create(context, R.drawable.ole_logo, in.title, "Task expires on " + TimeUtils.formatDate(in.deadline));
                in.isNotified = true;
            }
            mRealm.commitTransaction();
        }

        return Result.success();
    }
}

