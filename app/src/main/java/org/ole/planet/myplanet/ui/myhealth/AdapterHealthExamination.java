package org.ole.planet.myplanet.ui.myhealth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

public class AdapterHealthExamination extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmExamination> list;

    public AdapterHealthExamination(Context context, List<RealmExamination> list) {
        this.context = context;
        this.list = list;
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
            ((ViewHolderMyHealthExamination) holder).pulse.setText(list.get(position).getPulse());
            ((ViewHolderMyHealthExamination) holder).bp.setText(list.get(position).getBp());
            ((ViewHolderMyHealthExamination) holder).hearing.setText(list.get(position).getHearing());
            ((ViewHolderMyHealthExamination) holder).height.setText(list.get(position).getHeight());
            ((ViewHolderMyHealthExamination) holder).weight.setText(list.get(position).getWeight());
            ((ViewHolderMyHealthExamination) holder).vision.setText(list.get(position).getVision());
            holder.itemView.setOnClickListener(view -> showAlert(list.get(position)));
        }
    }

    private void showAlert(RealmExamination realmExamination) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_examination, null);
        TextView tvVitals = v.findViewById(R.id.tv_vitals);
        TextView tvCondition = v.findViewById(R.id.tv_condition);
        tvVitals.setText(
                        "Temperature : " + realmExamination.getTemperature() + "\n" +
                        "Pulse : " + realmExamination.getPulse() + "\n" +
                        "Blood Pressure : " + realmExamination.getBp() + "\n" +
                        "Height : " + realmExamination.getHeight() + "\n" +
                        "Weight : " + realmExamination.getWeight() + "\n" +
                        "Vision : " + realmExamination.getVision() + "\n" +
                        "Hearing : " + realmExamination.getHearing() + "\n"

        );

        tvCondition.setText(
                "Observations & Notes : " + realmExamination.getNotes() + "\n" +
                "Diagnosis : " + realmExamination.getDiagnosis() + "\n" +
                "Treatments : " + realmExamination.getTreatments() + "\n" +
                "Medications : " + realmExamination.getMedications() + "\n" +
                "Immunizations : " + realmExamination.getImmunizations() + "\n" +
                "Allergies : " + realmExamination.getAllergies() + "\n" +
                "X-rays : " + realmExamination.getXrays() + "\n" +
                "Lab Tests : " + realmExamination.getTests() + "\n" +
                "Referrals : " + realmExamination.getReferrals() + "\n"

        );
        new AlertDialog.Builder(context).setTitle(TimeUtils.formatDate(realmExamination.getDate(), "MMM dd, yyyy"))
                .setView(v)
                .setPositiveButton("OK", null).show();
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
