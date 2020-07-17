package org.ole.planet.myplanet.ui.team.teamTask;


import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nex3z.togglebuttongroup.SingleSelectToggleGroup;

import org.ole.planet.myplanet.R;
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

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamTaskFragment extends BaseTeamFragment implements AdapterTask.OnCompletedListener {

    RecyclerView rvTask;
    Calendar deadline;
    TextView datePicker, nodata;
    SingleSelectToggleGroup taskButton;
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

        TimePickerDialog timePickerDialog = new TimePickerDialog(getActivity(),
                (view, hourOfDay, minute) -> {
                    deadline.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    deadline.set(Calendar.MINUTE, minute);
                    if (datePicker != null)
                        datePicker.setText(TimeUtils.formatDate(deadline.getTimeInMillis(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
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
            datePicker.setText(TimeUtils.formatDate(t.getDeadline()));
            deadline = Calendar.getInstance();
            deadline.setTime(new Date(t.getDeadline()));
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
        t.setDeadline(deadline.getTimeInMillis());
        t.setTeamId(teamId);
        t.setUpdated(true);
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
        taskButton.setOnCheckedChangeListener((group, checkedId) -> {
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
        rvTask.setAdapter(adapterTask);
    }

    @Override
    public void onCheckChange(RealmTeamTask realmTeamTask, boolean b) {
        Utilities.log("CHECK CHANGED");
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        realmTeamTask.setCompleted(b);
        realmTeamTask.setUpdated(true);
        realmTeamTask.setCompletedTime(new Date().getTime());
        mRealm.commitTransaction();
        try {
            rvTask.getAdapter().notifyDataSetChanged();
        } catch (Exception err) {
        }
    }

    @Override
    public void onEdit(RealmTeamTask task) {
        showTaskAlert(task);
    }

    @Override
    public void onDelete(RealmTeamTask task) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        task.deleteFromRealm();
        Utilities.toast(getActivity(), "Task deleted successfully");
        mRealm.commitTransaction();
        adapterTask.notifyDataSetChanged();
        showNoData(nodata, rvTask.getAdapter().getItemCount());
    }

    @Override
    public void onClickMore(RealmTeamTask realmTeamTask) {
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        Spinner spnUser = v.findViewById(R.id.spn_user);
        List<RealmUserModel> userList = RealmMyTeam.getJoinedMemeber(teamId, mRealm);
        ArrayAdapter<RealmUserModel> adapter = new UserListArrayAdapter(getActivity(), android.R.layout.simple_list_item_1, userList);
        spnUser.setAdapter(adapter);
        new AlertDialog.Builder(getActivity()).setTitle(R.string.select_member)
                .setView(v).setCancelable(false).setPositiveButton("OK", (dialogInterface, i) -> {
            RealmUserModel user = ((RealmUserModel) spnUser.getSelectedItem());
            String userId = user.getId();
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            realmTeamTask.setAssignee(userId);
            Utilities.toast(getActivity(), getString(R.string.assign_task_to) + " " + user.getName());
            mRealm.commitTransaction();
            adapter.notifyDataSetChanged();
        }).show();
    }
}
