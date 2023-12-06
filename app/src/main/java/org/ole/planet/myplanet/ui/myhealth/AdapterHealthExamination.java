package org.ole.planet.myplanet.ui.myhealth;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.AlertExaminationBinding;
import org.ole.planet.myplanet.databinding.RowExaminationBinding;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.List;
import java.util.Set;

import io.realm.Realm;

public class AdapterHealthExamination extends RecyclerView.Adapter<AdapterHealthExamination.ViewHolderMyHealthExamination> {
    private RowExaminationBinding rowExaminationBinding;
    private Context context;
    private List<RealmMyHealthPojo> list;
    private RealmMyHealthPojo mh;
    private RealmUserModel userModel;
    private Realm mRealm;

    public AdapterHealthExamination(Context context, List<RealmMyHealthPojo> list, RealmMyHealthPojo mh, RealmUserModel userModel) {
        this.context = context;
        this.list = list;
        this.mh = mh;
        this.userModel = userModel;
    }

    public void setmRealm(Realm mRealm) {
        this.mRealm = mRealm;
    }

    @NonNull
    @Override
    public ViewHolderMyHealthExamination onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowExaminationBinding = RowExaminationBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderMyHealthExamination(rowExaminationBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderMyHealthExamination holder, int position) {
        rowExaminationBinding.txtTemp.setText(checkEmpty(list.get(position).getTemperature()));
        rowExaminationBinding.txtDate.setText(TimeUtils.formatDate(list.get(position).date, "MMM dd, yyyy"));
        JsonObject encrypted = list.get(position).getEncryptedDataAsJson(this.userModel);
        String createdBy = JsonUtils.getString("createdBy", encrypted);
        if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel.getId())) {
            RealmUserModel model = mRealm.where(RealmUserModel.class).equalTo("id", createdBy).findFirst();
            String name = "";
            if (model != null) {
                name = model.getFullName();
            } else {
                name = createdBy.split(":")[1];
            }
            rowExaminationBinding.txtDate.setText(rowExaminationBinding.txtDate.getText() + "\n" + name);
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.md_grey_50));
        } else {
            rowExaminationBinding.txtDate.setText(rowExaminationBinding.txtDate.getText() + context.getString(R.string.self_examination));
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.md_green_50));
        }
        rowExaminationBinding.txtPulse.setText(checkEmptyInt(list.get(position).pulse));
        rowExaminationBinding.txtBp.setText(list.get(position).bp);
        rowExaminationBinding.txtHearing.setText(list.get(position).hearing + "");
        rowExaminationBinding.txtHearing.setText(checkEmpty(list.get(position).height));
        rowExaminationBinding.txtWeight.setText(checkEmpty(list.get(position).getWeight()));
        rowExaminationBinding.txtVision.setText(list.get(position).vision + "");
        holder.itemView.setOnClickListener(view -> showAlert(position, encrypted));
    }

    private String checkEmpty(float value) {
        return value == 0 ? "" : value + "";
    }


    private String checkEmptyInt(int value) {
        return value == 0 ? "" : value + "";
    }

    private void showAlert(int position, JsonObject encrypted) {
        RealmMyHealthPojo realmExamination = list.get(position);

        AlertExaminationBinding alertExaminationBinding = AlertExaminationBinding.inflate(LayoutInflater.from(context));
        alertExaminationBinding.tvVitals.setText(context.getString(R.string.temperature_colon) + checkEmpty(realmExamination.getTemperature()) + "\n" + context.getString(R.string.pulse_colon) + checkEmptyInt(realmExamination.pulse) + "\n" + context.getString(R.string.blood_pressure_colon) + realmExamination.bp + "\n" + context.getString(R.string.height_colon) + checkEmpty(realmExamination.height) + "\n" + context.getString(R.string.weight_colon) + checkEmpty(realmExamination.getWeight()) + "\n" + context.getString(R.string.vision_colon) + realmExamination.vision + "\n" + context.getString(R.string.hearing_colon) + realmExamination.hearing + "\n");

        showConditions(alertExaminationBinding.tvCondition, realmExamination);
        showEncryptedData(alertExaminationBinding.tvOtherNotes, encrypted);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(TimeUtils.formatDate(realmExamination.date, "MMM dd, yyyy")).setView(alertExaminationBinding.getRoot()).setPositiveButton("OK", null).create();
        long time = new Date().getTime() - 5000 * 60;
        if (realmExamination.date >= time) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, context.getString(R.string.edit), (dialogInterface, i) -> context.startActivity(new Intent(context, AddExaminationActivity.class).putExtra("id", list.get(position).get_id()).putExtra("userId", mh.get_id())));
        }
        dialog.show();
    }

    private void showConditions(TextView tvCondition, RealmMyHealthPojo realmExamination) {
        JsonObject conditionsMap = new Gson().fromJson(realmExamination.conditions, JsonObject.class);
        Set<String> keys = conditionsMap.keySet();
        StringBuilder conditions = new StringBuilder();
        for (String key : keys) {
            if (conditionsMap.get(key).getAsBoolean()) {
                conditions.append(key + ", ");
            }
        }
        tvCondition.setText(conditions);
    }

    private void showEncryptedData(TextView tvOtherNotes, JsonObject encrypted) {
        tvOtherNotes.setText(R.string.observations_notes_colon + Utilities.checkNA(JsonUtils.getString("notes", encrypted)) + "\n" + R.string.diagnosis_colon + Utilities.checkNA(JsonUtils.getString("diagnosis", encrypted)) + "\n" + R.string.treatments_colon + Utilities.checkNA(JsonUtils.getString("treatments", encrypted)) + "\n" + R.string.medications_colon + Utilities.checkNA(JsonUtils.getString("medications", encrypted)) + "\n" + R.string.immunizations_colon + Utilities.checkNA(JsonUtils.getString("immunizations", encrypted)) + "\n" + R.string.allergies_colon + Utilities.checkNA(JsonUtils.getString("allergies", encrypted)) + "\n" + R.string.x_rays_colon + Utilities.checkNA(JsonUtils.getString("xrays", encrypted)) + "\n" + R.string.lab_tests_colon + Utilities.checkNA(JsonUtils.getString("tests", encrypted)) + "\n" + R.string.referrals_colon + Utilities.checkNA(JsonUtils.getString("referrals", encrypted)) + "\n");
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolderMyHealthExamination extends RecyclerView.ViewHolder {
        RowExaminationBinding rowExaminationBinding;

        public ViewHolderMyHealthExamination(RowExaminationBinding rowExaminationBinding) {
            super(rowExaminationBinding.getRoot());
            this.rowExaminationBinding = rowExaminationBinding;
        }
    }
}
