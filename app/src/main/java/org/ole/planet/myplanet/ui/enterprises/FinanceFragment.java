package org.ole.planet.myplanet.ui.enterprises;


import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.clans.fab.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.team.BaseTeamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * A simple {@link Fragment} subclass.
 */
public class FinanceFragment extends BaseTeamFragment {

    RecyclerView rvFinance;
    FloatingActionButton fab;
    TextView nodata;
    Realm mRealm;
    ImageView imgDate;
    AdapterFinance adapterFinance;
    TextInputLayout tlNote;
    Spinner spnType;
    TextInputLayout tlAmount;
    LinearLayout llDate;
    Calendar date;
    TextView tvSelectDate;
    RealmResults<RealmMyTeam> list;
    boolean isAsc = false;

    DatePickerDialog.OnDateSetListener listener = (view, year, monthOfYear, dayOfMonth) -> {
        date = Calendar.getInstance();
        date.set(Calendar.YEAR, year);
        date.set(Calendar.MONTH, monthOfYear);
        date.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        if (date != null)
            tvSelectDate.setText(TimeUtils.formatDateTZ(date.getTimeInMillis()));
    };

    public FinanceFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_finance, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        rvFinance = v.findViewById(R.id.rv_finance);
        fab = v.findViewById(R.id.add_transaction);
        nodata = v.findViewById(R.id.tv_nodata);
        imgDate = v.findViewById(R.id.img_date);
        llDate = v.findViewById(R.id.ll_date);
        date = Calendar.getInstance();
        v.findViewById(R.id.btn_filter).setOnClickListener(view -> {
            showDatePickerDialog();
        });
        llDate.setOnClickListener(view -> {
            imgDate.setRotation(imgDate.getRotation() + 180);
            list = mRealm.where(RealmMyTeam.class).notEqualTo("status", "archived").equalTo("teamId", teamId).equalTo("docType", "transaction").sort("date",isAsc? Sort.DESCENDING : Sort.ASCENDING).findAll();
            adapterFinance = new AdapterFinance(getActivity(), list);
            rvFinance.setAdapter(adapterFinance);
            isAsc = !isAsc;
        });
        return v;
    }

    private void showDatePickerDialog() {
        Calendar now = Calendar.getInstance();
        com.borax12.materialdaterangepicker.date.DatePickerDialog.newInstance(
                (view1, year, monthOfYear, dayOfMonth, yearEnd, monthOfYearEnd, dayOfMonthEnd) -> {
                    Calendar start = Calendar.getInstance();
                    Calendar end = Calendar.getInstance();
                    start.set(year, monthOfYear, dayOfMonth);
                    end.set(yearEnd, monthOfYearEnd, dayOfMonthEnd);
                    Utilities.log("" + start.getTimeInMillis() + " " + end.getTimeInMillis());
                    list = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId)
                            .equalTo("docType", "transaction")
                            .between("date", start.getTimeInMillis(), end.getTimeInMillis())
                            .sort("date", Sort.DESCENDING).findAll();
                    adapterFinance = new AdapterFinance(getActivity(), list);
                    rvFinance.setAdapter(adapterFinance);
                    calculateTotal(list);
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
        ).show(getActivity().getFragmentManager(), "");
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (user.isManager() || user.isLeader()){
            fab.setVisibility(View.VISIBLE);
        }else{
            fab.setVisibility(View.GONE);
        }
        fab.setOnClickListener(view -> addTransaction());
        list = mRealm.where(RealmMyTeam.class).notEqualTo("status", "archived").equalTo("teamId", teamId).equalTo("docType", "transaction").sort("date", Sort.DESCENDING).findAll();
        adapterFinance = new AdapterFinance(getActivity(), list);
        rvFinance.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvFinance.setAdapter(adapterFinance);
        calculateTotal(list);
        showNoData(nodata, list.size());
    }

    private void calculateTotal(List<RealmMyTeam> list) {
        int debit = 0;
        int credit = 0;
        for (RealmMyTeam team : list) {
            if ("credit".equalsIgnoreCase(team.getType().toLowerCase())) {
                credit += team.getAmount();
            } else {
                debit += team.getAmount();
            }
        }
        int total = credit - debit;
        ((TextView) getView().findViewById(R.id.tv_debit)).setText(debit + "");
        ((TextView) getView().findViewById(R.id.tv_credit)).setText(credit + "");
        ((TextView) getView().findViewById(R.id.tv_balance)).setText(total + "");
        if(total>=0)
            ((TextView) getView().findViewById(R.id.balance_caution)).setVisibility(View.GONE);

    }

    private void addTransaction() {
        new AlertDialog.Builder(getActivity()).setView(setUpAlertUi())
                .setTitle("Add Transaction")
                .setPositiveButton("Submit", (dialogInterface, i) -> {
                    String type = spnType.getSelectedItem().toString();
                    Utilities.log(type + " type");
                    String note = tlNote.getEditText().getText().toString().trim();
                    String amount = tlAmount.getEditText().getText().toString().trim();

                    if (note.isEmpty()) {
                        Utilities.toast(getActivity(), "Note is required");
                    } else if (amount.isEmpty()) {
                        Utilities.toast(getActivity(), "Amount is required");
                    } else if (date == null) {
                        Utilities.toast(getActivity(), "Date is required");
                    } else {
                        mRealm.executeTransactionAsync(realm -> {
                            createTransactionObject(realm, type, note, amount, date);
                        }, () -> {
                            Utilities.toast(getActivity(), "Transaction added");
                            adapterFinance.notifyDataSetChanged();
                            showNoData(nodata, adapterFinance.getItemCount());
                            calculateTotal(list);
                        });
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void createTransactionObject(Realm realm, String type, String note, String amount, Calendar date) {
        RealmMyTeam team = realm.createObject(RealmMyTeam.class, UUID.randomUUID().toString());
        team.setStatus("active");
        team.setDate(date.getTimeInMillis());
        if (type != null)
            team.setTeamType(type);
        team.setType(type);
        team.setDescription(note);
        team.setTeamId(teamId);
        team.setAmount(Integer.parseInt(amount));
        team.setParentCode(user.getParentCode());
        team.setTeamPlanetCode(user.getPlanetCode());
        team.setTeamType("sync");
        team.setDocType("transaction");
        team.setUpdated(true);

    }


    private View setUpAlertUi() {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.add_transaction, null);
        spnType = v.findViewById(R.id.spn_type);
        tlNote = v.findViewById(R.id.tl_note);
        tlAmount = v.findViewById(R.id.tl_amount);
        tvSelectDate = v.findViewById(R.id.tv_select_date);
        tvSelectDate.setOnClickListener(view -> new DatePickerDialog(getActivity(), listener, date
                .get(Calendar.YEAR), date.get(Calendar.MONTH),
                date.get(Calendar.DAY_OF_MONTH)).show());

        return v;
    }
}
