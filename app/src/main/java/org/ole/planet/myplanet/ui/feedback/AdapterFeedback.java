package org.ole.planet.myplanet.ui.feedback;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmFeedback;

import java.util.List;

public class AdapterFeedback extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    Context context;
    List<RealmFeedback> list;

    public AdapterFeedback(Context context, List<RealmFeedback> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_feedback, parent, false);
        return new ViewHolderFeedback(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderFeedback) {
            ((ViewHolderFeedback) holder).tvTitle.setText(list.get(position).getTitle());
            ((ViewHolderFeedback) holder).tvType.setText(list.get(position).getType());
            ((ViewHolderFeedback) holder).tvPriority.setText(list.get(position).getPriority());
            ((ViewHolderFeedback) holder).tvStatus.setText(list.get(position).getStatus());
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderFeedback extends RecyclerView.ViewHolder {
        TextView tvTitle, tvType, tvPriority, tvStatus;

        public ViewHolderFeedback(View itemView) {
            super(itemView);
            tvPriority = itemView.findViewById(R.id.tv_priority);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvType = itemView.findViewById(R.id.tv_type);
            tvTitle = itemView.findViewById(R.id.tv_title);
        }
    }
}
