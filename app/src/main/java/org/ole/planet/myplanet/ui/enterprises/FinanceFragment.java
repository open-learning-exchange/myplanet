package org.ole.planet.myplanet.ui.enterprises;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.AddTransactionBinding;
import org.ole.planet.myplanet.databinding.FragmentFinanceBinding;
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

public class FinanceFragment extends BaseTeamFragment {
    private FragmentFinanceBinding fragmentFinanceBinding;
    private AddTransactionBinding addTransactionBinding;
    Realm mRealm;
    AdapterFinance adapterFinance;
    Calendar date;
    RealmResults<RealmMyTeam> list;
    boolean isAsc = false;

    DatePickerDialog.OnDateSetListener listener = (view, year, monthOfYear, dayOfMonth) -> {
        date = Calendar.getInstance();
        date.set(Calendar.YEAR, year);
        date.set(Calendar.MONTH, monthOfYear);
        date.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        if (date != null) addTransactionBinding.tvSelectDate.setText(TimeUtils.formatDateTZ(date.getTimeInMillis()));
    };

    public FinanceFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentFinanceBinding = FragmentFinanceBinding.inflate(inflater, container, false);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        date = Calendar.getInstance();
        fragmentFinanceBinding.btnFilter.setOnClickListener(view -> {
            showDatePickerDialog();
        });
        fragmentFinanceBinding.llDate.setOnClickListener(view -> {
            fragmentFinanceBinding.imgDate.setRotation(fragmentFinanceBinding.imgDate.getRotation() + 180);
            list = mRealm.where(RealmMyTeam.class).notEqualTo("status", "archived").equalTo("teamId", teamId).equalTo("docType", "transaction").sort("date", isAsc ? Sort.DESCENDING : Sort.ASCENDING).findAll();
            adapterFinance = new AdapterFinance(getActivity(), list);
            fragmentFinanceBinding.rvFinance.setAdapter(adapterFinance);
            isAsc = !isAsc;
        });
        fragmentFinanceBinding.btnReset.setOnClickListener(view -> {
            list = mRealm.where(RealmMyTeam.class).notEqualTo("status", "archived").equalTo("teamId", teamId).equalTo("docType", "transaction").sort("date", Sort.DESCENDING).findAll();
            adapterFinance = new AdapterFinance(getActivity(), list);
            fragmentFinanceBinding.rvFinance.setLayoutManager(new LinearLayoutManager(getActivity()));
            fragmentFinanceBinding.rvFinance.setAdapter(adapterFinance);
            calculateTotal(list);
        });
        return fragmentFinanceBinding.getRoot();
    }

    private void showDatePickerDialog() {
        Calendar now = Calendar.getInstance();
        com.borax12.materialdaterangepicker.date.DatePickerDialog.newInstance((view1, year, monthOfYear, dayOfMonth, yearEnd, monthOfYearEnd, dayOfMonthEnd) -> {
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            start.set(year, monthOfYear, dayOfMonth);
            end.set(yearEnd, monthOfYearEnd, dayOfMonthEnd);
            Utilities.log("" + start.getTimeInMillis() + " " + end.getTimeInMillis());
            list = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("docType", "transaction").between("date", start.getTimeInMillis(), end.getTimeInMillis()).sort("date", Sort.DESCENDING).findAll();
            adapterFinance = new AdapterFinance(getActivity(), list);
            fragmentFinanceBinding.rvFinance.setAdapter(adapterFinance);
            calculateTotal(list);
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show(getActivity().getFragmentManager(), "");

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (user.isManager() || user.isLeader()) {
            fragmentFinanceBinding.addTransaction.setVisibility(View.VISIBLE);
        } else {
            fragmentFinanceBinding.addTransaction.setVisibility(View.GONE);
        }
        fragmentFinanceBinding.addTransaction.setOnClickListener(view -> addTransaction());
        list = mRealm.where(RealmMyTeam.class).notEqualTo("status", "archived").equalTo("teamId", teamId).equalTo("docType", "transaction").sort("date", Sort.DESCENDING).findAll();
        adapterFinance = new AdapterFinance(getActivity(), list);
        fragmentFinanceBinding.rvFinance.setLayoutManager(new LinearLayoutManager(getActivity()));
        fragmentFinanceBinding.rvFinance.setAdapter(adapterFinance);
        calculateTotal(list);
        showNoData(fragmentFinanceBinding.tvNodata, list.size());
    }

    private void calculateTotal(List<RealmMyTeam> list) {
        int debit = 0;
        int credit = 0;
        for (RealmMyTeam team : list) {
            if ("credit".equalsIgnoreCase(team.type.toLowerCase())) {
                credit += team.amount;
            } else {
                debit += team.amount;
            }
        }
        int total = credit - debit;
        fragmentFinanceBinding.tvDebit.setText(debit + "");
        fragmentFinanceBinding.tvCredit.setText(credit + "");
        fragmentFinanceBinding.tvBalance.setText(total + "");
        if (total >= 0)
            fragmentFinanceBinding.balanceCaution.setVisibility(View.GONE);

    }

    private void addTransaction() {
        new AlertDialog.Builder(getActivity()).setView(setUpAlertUi()).setTitle(R.string.add_transaction).setPositiveButton("Submit", (dialogInterface, i) -> {
            String type = addTransactionBinding.spnType.getSelectedItem().toString();
            Utilities.log(type + " type");
            String note = addTransactionBinding.tlNote.getEditText().getText().toString().trim();
            String amount = addTransactionBinding.tlAmount.getEditText().getText().toString().trim();

            if (note.isEmpty()) {
                Utilities.toast(getActivity(), getString(R.string.note_is_required));
            } else if (amount.isEmpty()) {
                Utilities.toast(getActivity(), getString(R.string.amount_is_required));
            } else if (date == null) {
                Utilities.toast(getActivity(), getString(R.string.date_is_required));
            } else {
                mRealm.executeTransactionAsync(realm -> {
                    createTransactionObject(realm, type, note, amount, date);
                }, () -> {
                    Utilities.toast(getActivity(), getString(R.string.transaction_added));
                    adapterFinance.notifyDataSetChanged();
                    showNoData(fragmentFinanceBinding.tvNodata, adapterFinance.getItemCount());
                    calculateTotal(list);
                });
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void createTransactionObject(Realm realm, String type, String note, String amount, Calendar date) {
        RealmMyTeam team = realm.createObject(RealmMyTeam.class, UUID.randomUUID().toString());
        team.status = "active";
        team.date = date.getTimeInMillis();
        if (type != null) team.teamType = type;
        team.type = type;
        team.description = note;
        team.teamId = teamId;
        team.amount = Integer.parseInt(amount);
        team.parentCode = user.getParentCode();
        team.teamPlanetCode = user.getPlanetCode();
        team.teamType = "sync";
        team.docType = "transaction";
        team.updated = true;
    }

    private View setUpAlertUi() {
        addTransactionBinding = AddTransactionBinding.inflate(LayoutInflater.from(getActivity())); // Replace with your actual binding class name
        addTransactionBinding.tvSelectDate.setOnClickListener(view -> new DatePickerDialog(getActivity(), listener, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show());

        return addTransactionBinding.getRoot();
    }
}
