package org.ole.planet.myplanet.ui.myhealth;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyHealth;

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
            ((ViewHolderMyHealthExamination) holder).name.setText(list.get(position).getBp());
            ((ViewHolderMyHealthExamination) holder).specialNeed.setText(list.get(position).getTemperature());
            ((ViewHolderMyHealthExamination) holder).otherNeed.setText(list.get(position).getPulse());
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderMyHealthExamination extends RecyclerView.ViewHolder {
        TextView name, specialNeed, otherNeed;

        public ViewHolderMyHealthExamination(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_title);
            specialNeed = itemView.findViewById(R.id.tv_special_needs);
            otherNeed = itemView.findViewById(R.id.tv_other_needs);
        }
    }
}
