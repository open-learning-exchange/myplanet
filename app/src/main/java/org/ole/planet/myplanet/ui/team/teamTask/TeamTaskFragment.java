package org.ole.planet.myplanet.ui.team.teamTask;


import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.team.AdapterTeam;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class TeamTaskFragment extends BaseTeamFragment implements AdapterTask.OnCompletedListener {

    RecyclerView rvTask;
    Calendar deadline;
    TextView datePicker, nodata;
    DatePickerDialog.OnDateSetListener listener = (view, year, monthOfYear, dayOfMonth) -> {
        deadline = Calendar.getInstance();
        deadline.set(Calendar.YEAR, year);
        deadline.set(Calendar.MONTH, monthOfYear);
        deadline.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        if (datePicker != null)
            datePicker.setText(TimeUtils.formatDateTZ(deadline.getTimeInMillis()));
    };

    public TeamTaskFragment() {
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_team_task, container, false);
        rvTask = v.findViewById(R.id.rv_task);
        nodata = v.findViewById(R.id.tv_nodata);
        v.findViewById(R.id.fab).setOnClickListener(view -> {
            showTaskAlert();
        });
        return v;
    }

    private void showTaskAlert() {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.alert_task, null);
        EditText title = v.findViewById(R.id.et_task);
        EditText description = v.findViewById(R.id.et_description);
        datePicker = v.findViewById(R.id.tv_pick);
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
                createNewTask(task, desc);
        }).setNegativeButton("Cancel", null).show();
    }

    private void createNewTask(String task, String desc) {
        mRealm.executeTransactionAsync(realm -> {
            RealmTeamTask t = realm.createObject(RealmTeamTask.class, UUID.randomUUID().toString());
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
            t.setCompleted(false);
        }, () -> {
            if (rvTask.getAdapter() != null) {
                rvTask.getAdapter().notifyDataSetChanged();
                showNoData(nodata, rvTask.getAdapter().getItemCount());
            }
            Utilities.toast(getActivity(), "Task added successfully");
        });
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        rvTask.setLayoutManager(new LinearLayoutManager(getActivity()));
        List<RealmTeamTask> list = mRealm.where(RealmTeamTask.class).equalTo("teamId", teamId).findAll();
        Utilities.log("List size " + list.size());
        AdapterTask adapterTask = new AdapterTask(getActivity(), list);
        adapterTask.setListener(this);
        rvTask.setAdapter(adapterTask);
        showNoData(nodata, list.size());
    }

    @Override
    public void onCheckChange(RealmTeamTask realmTeamTask, boolean b) {
        Utilities.log("cHECK CHANGED");
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        realmTeamTask.setCompleted(b);
        mRealm.commitTransaction();
    }

    @Override
    public void onClickMore(RealmTeamTask realmTeamTask) {
        View v = getLayoutInflater().inflate(R.layout.alert_users_spinner, null);
        Spinner spnUser = v.findViewById(R.id.spn_user);
        List<RealmUserModel> userList = mRealm.where(RealmUserModel.class).findAll();
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, userList);
        spnUser.setAdapter(adapter);
        new AlertDialog.Builder(getActivity()).setTitle("Select Patient")
                .setView(v).setCancelable(false).setPositiveButton("OK", (dialogInterface, i) -> {
            RealmUserModel user = ((RealmUserModel) spnUser.getSelectedItem());
            String userId = user.getId();
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            realmTeamTask.setAssignee(userId);
            mRealm.commitTransaction();
            Utilities.toast(getActivity(), "Task Assigned to " + user.getName());
        }).show();
    }
}
