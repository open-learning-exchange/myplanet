package org.ole.planet.myplanet.ui.myhealth;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.userprofile.AdapterOtherInfo;

import java.util.List;

import io.realm.Realm;

public class AdapterVitalSign extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmVitalSign> list;
    RealmUserModel userModel;
    Realm mRealm;
    public AdapterVitalSign(Context context, List<RealmVitalSign> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_vital_sign, parent, false);
        return new ViewHolderVitalSign(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        mRealm = new DatabaseService(context).getRealmInstance();
        if (holder instanceof ViewHolderVitalSign) {
            if (pos == 0) {
                ((ViewHolderVitalSign) holder).name.setText(Html.fromHtml("<b>Name</b>"));
                ((ViewHolderVitalSign) holder).pulseRate.setText(Html.fromHtml("<b>Pulse Rate</b>"));
                ((ViewHolderVitalSign) holder).respRate.setText(Html.fromHtml("<b>Respiration Rate</b>"));
                ((ViewHolderVitalSign) holder).bloodPressure.setText(Html.fromHtml("<b>Blood Pressure</b>"));

            } else {
                int position = pos - 1;
                userModel = mRealm.where(RealmUserModel.class).equalTo("id", list.get(position).getUserId()).findFirst();
                ((ViewHolderVitalSign) holder).name.setText(userModel.getFullName());
                ((ViewHolderVitalSign) holder).pulseRate.setText(list.get(position).getPulseRate() + "");
                ((ViewHolderVitalSign) holder).respRate.setText(list.get(position).getRespirationRate() + "");
                ((ViewHolderVitalSign) holder).bloodPressure.setText(String.format("%d/%d", list.get(position).getBloodPressureSystolic(), list.get(position).getBloodPressureDiastolic()));
                holder.itemView.setOnClickListener(view -> context.startActivity(new Intent(context, MyHealthDetailActivity.class).putExtra("id", list.get(position).getId())));
            }
        }
    }

    @Override
    public int getItemCount() {
        return list.size() + 1;
    }

    class ViewHolderVitalSign extends RecyclerView.ViewHolder {
        TextView name, pulseRate, respRate, bloodPressure;

        public ViewHolderVitalSign(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_title);
            pulseRate = itemView.findViewById(R.id.tv_pulse_rate);
            respRate = itemView.findViewById(R.id.tv_resp_rate);
            bloodPressure = itemView.findViewById(R.id.tv_blood_pressure);
        }
    }
}
