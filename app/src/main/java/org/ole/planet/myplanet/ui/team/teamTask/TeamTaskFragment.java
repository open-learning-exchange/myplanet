package org.ole.planet.myplanet.ui.team.teamTask;

import static org.ole.planet.myplanet.MainApplication.context;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.AlertTaskBinding;
import org.ole.planet.myplanet.databinding.AlertUsersSpinnerBinding;
import org.ole.planet.myplanet.databinding.FragmentTeamTaskBinding;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.myhealth.UserListArrayAdapter;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Sort;

public class TeamTaskFragment extends BaseTeamFragment implements AdapterTask.OnCompletedListener {
    private FragmentTeamTaskBinding fragmentTeamTaskBinding;
    Calendar deadline;
    TextView datePicker;
    List<RealmTeamTask> list;
    AdapterTask adapterTask;

    DatePickerDialog.OnDateSetListener listener = (view, year, monthOfYear, dayOfMonth) -> {
        deadline = Calendar.getInstance();
        deadline.set(Calendar.YEAR, year);
        deadline.set(Calendar.MONTH, monthOfYear);
        deadline.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        if (datePicker != null)
            datePicker.setText(TimeUtils.formatDateTZ(deadline.getTimeInMillis()));
        timePicker();
    };

    public TeamTaskFragment() {
    }

    private void timePicker() {

        TimePickerDialog timePickerDialog = new TimePickerDialog(getActivity(), (view, hourOfDay, minute) -> {
            deadline.set(Calendar.HOUR_OF_DAY, hourOfDay);
            deadline.set(Calendar.MINUTE, minute);
            if (datePicker != null)
                datePicker.setText(TimeUtils.formatDate(deadline.getTimeInMillis(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        }, deadline.get(Calendar.HOUR_OF_DAY), deadline.get(Calendar.MINUTE), true);
        timePickerDialog.show();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentTeamTaskBinding = FragmentTeamTaskBinding.inflate(inflater, container, false);
        fragmentTeamTaskBinding.fab.setOnClickListener(view -> showTaskAlert(null));
        return fragmentTeamTaskBinding.getRoot();
    }

    private void showTaskAlert(RealmTeamTask t) {
        AlertTaskBinding alertTaskBinding = AlertTaskBinding.inflate(getLayoutInflater());

        datePicker = alertTaskBinding.tvPick;
        if (t != null) {
            alertTaskBinding.etTask.setText(t.title);
            alertTaskBinding.etDescription.setText(t.description);
            datePicker.setText(TimeUtils.formatDate(t.deadline));
            deadline = Calendar.getInstance();
            deadline.setTime(new Date(t.deadline));
        }

        Calendar myCalendar = Calendar.getInstance();
        datePicker.setOnClickListener(view -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    getContext(), listener, myCalendar.get(Calendar.YEAR),
                    myCalendar.get(Calendar.MONTH), myCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.getDatePicker().setMinDate(myCalendar.getTimeInMillis());
            datePickerDialog.show();
        });

        new AlertDialog.Builder(getActivity()).setTitle(R.string.add_task).setView(alertTaskBinding.getRoot()).setPositiveButton(R.string.save, (dialogInterface, i) -> {
            String task = alertTaskBinding.etTask.getText().toString();
            String desc = alertTaskBinding.etDescription.getText().toString();
            if (task.isEmpty()) Utilities.toast(getActivity(), getString(R.string.task_title_is_required));
            else if (deadline == null) Utilities.toast(getActivity(), getString(R.string.deadline_is_required));
            else createOrUpdateTask(task, desc, t);
        }).setNegativeButton(getString(R.string.cancel), null).show();
    }

    private void createOrUpdateTask(String task, String desc, RealmTeamTask t) {
        boolean isCreate = (t == null);
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        if (t == null) t = mRealm.createObject(RealmTeamTask.class, UUID.randomUUID().toString());
        t.title = task;
        t.description = desc;
        t.deadline = deadline.getTimeInMillis();
        t.teamId = teamId;
        t.isUpdated = true;
        JsonObject ob = new JsonObject();
        ob.addProperty("teams", teamId);
        t.link = new Gson().toJson(ob);
        JsonObject obsync = new JsonObject();
        obsync.addProperty("type", "local");
        obsync.addProperty("planetCode", user.getPlanetCode());
        t.sync = new Gson().toJson(obsync);
        mRealm.commitTransaction();
        if (fragmentTeamTaskBinding.rvTask.getAdapter() != null) {
            fragmentTeamTaskBinding.rvTask.getAdapter().notifyDataSetChanged();
            showNoData(fragmentTeamTaskBinding.tvNodata, fragmentTeamTaskBinding.rvTask.getAdapter().getItemCount());
        }
        Utilities.toast(getActivity(), String.format(getString(R.string.task_s_successfully), isCreate ? getString(R.string.added) : getString(R.string.updated)));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fragmentTeamTaskBinding.rvTask.setLayoutManager(new LinearLayoutManager(getActivity()));
        list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).findAll();
        setAdapter();
        showNoData(fragmentTeamTaskBinding.tvNodata, list.size());
        fragmentTeamTaskBinding.taskToggle.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.btn_my) {
                list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).notEqualTo("status", "archived").equalTo("completed", false).equalTo("assignee", user.getId()).sort("deadline", Sort.DESCENDING).findAll();
            } else if (checkedId == R.id.btn_completed) {
                list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).notEqualTo("status", "archived").equalTo("completed", true).sort("deadline", Sort.DESCENDING).findAll();
            } else {
                list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).notEqualTo("status", "archived").sort("completed", Sort.ASCENDING).findAll();
            }
            setAdapter();
        });
    }

    private void setAdapter() {
        adapterTask = new AdapterTask(getActivity(), mRealm, list);
        adapterTask.setListener(this);
        fragmentTeamTaskBinding.rvTask.setAdapter(adapterTask);
    }

    @Override
    public void onCheckChange(RealmTeamTask realmTeamTask, boolean b) {
        Utilities.log("CHECK CHANGED");
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        realmTeamTask.completed = b;
        realmTeamTask.isUpdated = true;
        realmTeamTask.completedTime = new Date().getTime();
        mRealm.commitTransaction();
        try {
            fragmentTeamTaskBinding.rvTask.getAdapter().notifyDataSetChanged();
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    @Override
    public void onEdit(RealmTeamTask task) {
        showTaskAlert(task);
    }

    @Override
    public void onDelete(RealmTeamTask task) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        task.deleteFromRealm();
        Utilities.toast(getActivity(), getString(R.string.task_deleted_successfully));
        mRealm.commitTransaction();
        adapterTask.notifyDataSetChanged();
        showNoData(fragmentTeamTaskBinding.tvNodata, fragmentTeamTaskBinding.rvTask.getAdapter().getItemCount());
    }

    @Override
    public void onClickMore(RealmTeamTask realmTeamTask) {
        AlertUsersSpinnerBinding alertUsersSpinnerBinding = AlertUsersSpinnerBinding.inflate(LayoutInflater.from(context));
        List<RealmUserModel> userList = RealmMyTeam.getJoinedMemeber(teamId, mRealm);
        ArrayAdapter<RealmUserModel> adapter = new UserListArrayAdapter(getActivity(), android.R.layout.simple_list_item_1, userList);
        alertUsersSpinnerBinding.spnUser.setAdapter(adapter);
        new AlertDialog.Builder(getActivity()).setTitle(R.string.select_member).setView(alertUsersSpinnerBinding.getRoot()).setCancelable(false).setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            RealmUserModel user = ((RealmUserModel) alertUsersSpinnerBinding.spnUser.getSelectedItem());
            String userId = user.getId();
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
            realmTeamTask.assignee = userId;
            Utilities.toast(getActivity(), getString(R.string.assign_task_to) + " " + user.getName());
            mRealm.commitTransaction();
            adapter.notifyDataSetChanged();
        }).show();
    }
}
