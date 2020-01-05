package org.ole.planet.myplanet.ui.myhealth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyHealth;
import org.ole.planet.myplanet.model.RealmMyHealthPojo;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.Date;
import java.util.List;

import io.realm.Realm;

public class AdapterHealthExamination extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmExamination> list;
    private RealmMyHealthPojo mh;
    private RealmUserModel userModel;
    private Realm mRealm;

    public AdapterHealthExamination(Context context, List<RealmExamination> list, RealmMyHealthPojo mh, RealmUserModel userModel) {
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

            ((ViewHolderMyHealthExamination) holder).temp.setText(list.get(position).getTemperature());
            ((ViewHolderMyHealthExamination) holder).date.setText(TimeUtils.formatDate(list.get(position).getDate(), "MMM dd, yyyy"));
            if (!TextUtils.isEmpty(list.get(position).getAddedBy()) && !TextUtils.equals(list.get(position).getAddedBy(), userModel.getId())) {
                RealmUserModel model = mRealm.where(RealmUserModel.class).equalTo("id", list.get(position).getAddedBy()).findFirst();
                String name = "";
                if (model != null) {
                    name = model.getFullName();
                } else {
                    name = list.get(position).getAddedBy().split(":")[1];
                }
                ((ViewHolderMyHealthExamination) holder).date.setText(((ViewHolderMyHealthExamination) holder).date.getText() + "\n" + name);
                holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.md_grey_50));
            } else {
                ((ViewHolderMyHealthExamination) holder).date.setText(((ViewHolderMyHealthExamination) holder).date.getText() + "\nSelf Examination");
                holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.md_green_50));

            }
            ((ViewHolderMyHealthExamination) holder).pulse.setText(list.get(position).getPulse());
            ((ViewHolderMyHealthExamination) holder).bp.setText(list.get(position).getBp());
            ((ViewHolderMyHealthExamination) holder).hearing.setText(list.get(position).getHearing());
            ((ViewHolderMyHealthExamination) holder).height.setText(list.get(position).getHeight());
            ((ViewHolderMyHealthExamination) holder).weight.setText(list.get(position).getWeight());
            ((ViewHolderMyHealthExamination) holder).vision.setText(list.get(position).getVision());
            holder.itemView.setOnClickListener(view -> showAlert(position));
        }
    }

    private void showAlert(int position) {
        RealmExamination realmExamination = list.get(position);
        View v = LayoutInflater.from(context).inflate(R.layout.alert_examination, null);
        TextView tvVitals = v.findViewById(R.id.tv_vitals);
        TextView tvCondition = v.findViewById(R.id.tv_condition);
        tvVitals.setText("Temperature : " + realmExamination.getTemperature() + "\n" +
                "Pulse : " + realmExamination.getPulse() + "\n" +
                "Blood Pressure : " + realmExamination.getBp() + "\n" +
                "Height : " + realmExamination.getHeight() + "\n" +
                "Weight : " + realmExamination.getWeight() + "\n" +
                "Vision : " + realmExamination.getVision() + "\n" +
                "Hearing : " + realmExamination.getHearing() + "\n");

        tvCondition.setText("Observations & Notes : " + realmExamination.getNotes() + "\n" + "Diagnosis : " + realmExamination.getDiagnosis() + "\n" + "Diagnosis Note : " + realmExamination.getDiagnosisNote() + "\n" +
                "Treatments : " + realmExamination.getTreatments() + "\n" + "Medications : " + realmExamination.getMedications() + "\n" + "Immunizations : " + realmExamination.getImmunizations() + "\n" + "Allergies : " + realmExamination.getAllergies() + "\n" + "X-rays : " + realmExamination.getXrays() + "\n" + "Lab Tests : " + realmExamination.getTests() + "\n" + "Referrals : " + realmExamination.getReferrals() + "\n");
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(TimeUtils.formatDate(realmExamination.getDate(), "MMM dd, yyyy"))
                .setView(v)
                .setPositiveButton("OK", null).create();
        long time = new Date().getTime() - 5000 * 60;
        if (realmExamination.getDate() >= time) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Edit", (dialogInterface, i) -> context.startActivity(new Intent(context, AddExaminationActivity.class).putExtra("position", position).putExtra("userId", mh.get_id())));
        }
        dialog.show();
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
