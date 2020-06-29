package org.ole.planet.myplanet.ui.myhealth;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Date;
import java.util.List;
import java.util.Set;

import io.realm.Realm;

public class AdapterHealthExamination extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_examination, parent, false);
        return new ViewHolderMyHealthExamination(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderMyHealthExamination) {

            ((ViewHolderMyHealthExamination) holder).temp.setText(checkEmpty(list.get(position).getTemperature()));
            ((ViewHolderMyHealthExamination) holder).date.setText(TimeUtils.formatDate(list.get(position).getDate(), "MMM dd, yyyy"));
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
                ((ViewHolderMyHealthExamination) holder).date.setText(((ViewHolderMyHealthExamination) holder).date.getText() + "\n" + name);
                holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.md_grey_50));
            } else {
                ((ViewHolderMyHealthExamination) holder).date.setText(((ViewHolderMyHealthExamination) holder).date.getText() + "\nSelf Examination");
                holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.md_green_50));
            }
            ((ViewHolderMyHealthExamination) holder).pulse.setText(checkEmptyInt(list.get(position).getPulse()));
            ((ViewHolderMyHealthExamination) holder).bp.setText(list.get(position).getBp());
            ((ViewHolderMyHealthExamination) holder).hearing.setText(list.get(position).getHearing() + "");
            ((ViewHolderMyHealthExamination) holder).height.setText(checkEmpty(list.get(position).getHeight()));
            ((ViewHolderMyHealthExamination) holder).weight.setText(checkEmpty(list.get(position).getWeight()));
            ((ViewHolderMyHealthExamination) holder).vision.setText(list.get(position).getVision() + "");
            holder.itemView.setOnClickListener(view -> showAlert(position, encrypted));
        }
    }

    private String checkEmpty(float value) {
        return value == 0 ? "" : value + "";
    }


    private String checkEmptyInt(int value) {
        return value == 0 ? "" : value + "";
    }


    private void showAlert(int position, JsonObject encrypted) {
        RealmMyHealthPojo realmExamination = list.get(position);

        View v = LayoutInflater.from(context).inflate(R.layout.alert_examination, null);
        TextView tvVitals = v.findViewById(R.id.tv_vitals);
        TextView tvCondition = v.findViewById(R.id.tv_condition);
        TextView tvOtherNotes = v.findViewById(R.id.tv_other_notes);
        tvVitals.setText("Temperature : " + checkEmpty(realmExamination.getTemperature()) + "\n" +
                "Pulse : " + checkEmptyInt(realmExamination.getPulse()) + "\n" +
                "Blood Pressure : " + realmExamination.getBp() + "\n" +
                "Height : " + checkEmpty(realmExamination.getHeight()) + "\n" +
                "Weight : " + checkEmpty(realmExamination.getWeight()) + "\n" +
                "Vision : " + realmExamination.getVision() + "\n" +
                "Hearing : " + realmExamination.getHearing() + "\n"
        );

      showConditions(tvCondition, realmExamination);
        showEncryptedData(tvOtherNotes, encrypted);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(TimeUtils.formatDate(realmExamination.getDate(), "MMM dd, yyyy"))
                .setView(v)
                .setPositiveButton("OK", null).create();
        long time = new Date().getTime() - 5000 * 60;
        if (realmExamination.getDate() >= time) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Edit", (dialogInterface, i) -> context.startActivity(new Intent(context, AddExaminationActivity.class).putExtra("id", list.get(position).get_id()).putExtra("userId", mh.get_id())));
        }
        dialog.show();
    }

    private void showConditions(TextView tvCondition, RealmMyHealthPojo realmExamination) {
        JsonObject conditionsMap = new Gson().fromJson(realmExamination.getConditions(), JsonObject.class);
        Set<String> keys = conditionsMap.keySet();
        StringBuilder conditions = new StringBuilder();
        for (String key : keys) {
            if (conditionsMap.get(key).getAsBoolean()) {
                conditions.append(key +", ");
            }
        }
        tvCondition.setText(conditions);
    }

    private void showEncryptedData(TextView tvOtherNotes, JsonObject encrypted) {
        tvOtherNotes.setText("Observations & Notes : " + Utilities.checkNA(JsonUtils.getString("notes", encrypted)) + "\n"
                + "Diagnosis : " + Utilities.checkNA(JsonUtils.getString("diagnosis", encrypted)) + "\n"
                + "Treatments : " + Utilities.checkNA(JsonUtils.getString("treatments", encrypted))
                + "\n" + "Medications : " + Utilities.checkNA(JsonUtils.getString("medications", encrypted))
                + "\n" + "Immunizations : " + Utilities.checkNA(JsonUtils.getString("immunizations", encrypted))
                + "\n" + "Allergies : " + Utilities.checkNA(JsonUtils.getString("allergies", encrypted)) +
                "\n" + "X-rays : " + Utilities.checkNA(JsonUtils.getString("xrays", encrypted)) + "\n" +
                "Lab Tests : " + Utilities.checkNA(JsonUtils.getString("tests", encrypted)) + "\n" +
                "Referrals : " + Utilities.checkNA(JsonUtils.getString("referrals", encrypted)) + "\n");
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderMyHealthExamination extends RecyclerView.ViewHolder {
        TextView temp, pulse, bp, height, weight, vision, hearing, date;

        public ViewHolderMyHealthExamination(View itemView) {
            super(itemView);
            temp = itemView.findViewById(R.id.txt_temp);
            pulse = itemView.findViewById(R.id.txt_pulse);
            date = itemView.findViewById(R.id.txt_date);
            bp = itemView.findViewById(R.id.txt_bp);
            height = itemView.findViewById(R.id.txt_height);
            hearing = itemView.findViewById(R.id.txt_hearing);
            weight = itemView.findViewById(R.id.txt_weight);
            vision = itemView.findViewById(R.id.txt_vision);
        }
    }
}
