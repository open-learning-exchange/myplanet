package org.ole.planet.myplanet.ui.team.teamTask;


import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamTaskFragment extends BaseTeamFragment implements AdapterTask.OnCompletedListener {

    RecyclerView rvTask;
    Calendar deadline;
    TextView datePicker, nodata;
    ToggleButton taskButton;
    List<RealmTeamTask> list;
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

        TimePickerDialog timePickerDialog = new TimePickerDialog(getActivity(),
                (view, hourOfDay, minute) -> {
                    deadline.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    deadline.set(Calendar.MINUTE, minute);
                    if (datePicker != null)
                        datePicker.setText(TimeUtils.formatDateTZ(deadline.getTimeInMillis()));
                }, deadline.get(Calendar.HOUR_OF_DAY), deadline.get(Calendar.MINUTE), true);
        timePickerDialog.show();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_team_task, container, false);
        rvTask = v.findViewById(R.id.rv_task);
        taskButton = v.findViewById(R.id.task_toggle);
        nodata = v.findViewById(R.id.tv_nodata);
        v.findViewById(R.id.fab).setOnClickListener(view -> {
            showTaskAlert(null);
        });
        return v;
    }

    private void showTaskAlert(RealmTeamTask t) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_task, null);
        EditText title = v.findViewById(R.id.et_task);
        EditText description = v.findViewById(R.id.et_description);

        datePicker = v.findViewById(R.id.tv_pick);
        if (t != null) {
            title.setText(t.getTitle());
            description.setText(t.getDescription());
            datePicker.setText(t.getDeadline());
            deadline = Calendar.getInstance();
            deadline.setTime(new Date(t.getExpire()));
        }

        Calendar myCalendar = Calendar.getInstance();
        datePicker.setOnClickListener(view -> new DatePickerDialog(getActivity(), listener, myCalendar
                .get(Calendar.YEAR), myCalendar.get(Calendar.MONTH),
                myCalendar.get(Calendar.DAY_OF_MONTH)).show());

        new AlertDialog.Builder(getActivity()).setTitle("Add Task").setView(v).setPositiveButton("Save", (dialogInterface, i) -> {
            String task = title.getText().toString();
            String desc = description.getText().toString();
            if (task.isEmpty())
                Utilities.toast(getActivity(), "Task title is required");
            else if (deadline == null)
                Utilities.toast(getActivity(), "Deadline is required");
            else
                createOrUpdateTask(task, desc, t);
        }).setNegativeButton("Cancel", null).show();
    }

    private void createOrUpdateTask(String task, String desc, RealmTeamTask t) {
        boolean isCreate = (t == null);
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        if (t == null)
            t = mRealm.createObject(RealmTeamTask.class, UUID.randomUUID().toString());
        t.setTitle(task);
        t.setDescription(desc);
        t.setDeadline(TimeUtils.formatDateTZ(deadline.getTimeInMillis()));
        t.setTeamId(teamId);
        JsonObject ob = new JsonObject();
        ob.addProperty("teams", teamId);
        t.setLink(new Gson().toJson(ob));
        JsonObject obsync = new JsonObject();
        obsync.addProperty("type", "local");
        obsync.addProperty("planetCode", user.getPlanetCode());
        t.setSync(new Gson().toJson(obsync));
        mRealm.commitTransaction();
        if (rvTask.getAdapter() != null) {
            rvTask.getAdapter().notifyDataSetChanged();
            showNoData(nodata, rvTask.getAdapter().getItemCount());
        }
        Utilities.toast(getActivity(), String.format("Task %s successfully", isCreate ? "added" : "updated"));

    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvTask.setLayoutManager(new LinearLayoutManager(getActivity()));
        list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).findAll();
        setAdapter();
        showNoData(nodata, list.size());
        taskButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).equalTo("assignee", user.getId()).findAll();
                } else {
                    list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).findAll();
                }
                setAdapter();
            }
        });
    }

    private void setAdapter() {
        AdapterTask adapterTask = new AdapterTask(getActivity(), mRealm, list);
        adapterTask.setListener(this);
        rvTask.setAdapter(adapterTask);
    }

    @Override
    public void onCheckChange(RealmTeamTask realmTeamTask, boolean b) {
        Utilities.log("CHECK CHANGED");
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        realmTeamTask.setCompleted(b);
        mRealm.commitTransaction();
    }

    @Override
    public void onEdit(RealmTeamTask task) {
        showTaskAlert(task);
    }

    @Override
    public void onClickMore(RealmTeamTask realmTeamTask) {
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        Spinner spnUser = v.findViewById(R.id.spn_user);
        List<RealmUserModel> userList = mRealm.where(RealmUserModel.class).findAll();
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, userList);
        spnUser.setAdapter(adapter);
        new AlertDialog.Builder(getActivity()).setTitle(R.string.select_member)
                .setView(v).setCancelable(false).setPositiveButton("OK", (dialogInterface, i) -> {
            RealmUserModel user = ((RealmUserModel) spnUser.getSelectedItem());
            String userId = user.getId();
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            realmTeamTask.setAssignee(userId);
            mRealm.commitTransaction();
            Utilities.toast(getActivity(), getString(R.string.assign_task_to) + user.getName());
            adapter.notifyDataSetChanged();
        }).show();
    }
}
