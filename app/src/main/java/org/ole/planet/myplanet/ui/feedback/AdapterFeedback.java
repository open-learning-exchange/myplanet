package org.ole.planet.myplanet.ui.feedback;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

public class AdapterFeedback extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmFeedback> list;

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
            if ("yes".equalsIgnoreCase(list.get(position).getPriority()))
                ((ViewHolderFeedback) holder).tvPriority.setBackground(context.getResources().getDrawable(R.drawable.bg_primary));
            else
                ((ViewHolderFeedback) holder).tvPriority.setBackground(context.getResources().getDrawable(R.drawable.bg_grey));
            ((ViewHolderFeedback) holder).tvStatus.setBackground(context.getResources().getDrawable("open".equalsIgnoreCase(list.get(position).getStatus()) ? R.drawable.bg_primary : R.drawable.bg_grey));
            ((ViewHolderFeedback) holder).tvOpenDate.setText(TimeUtils.getFormatedDate(Long.parseLong(list.get(position).getOpenTime())));
            holder.itemView.setOnClickListener(v -> context.startActivity(new Intent(context, FeedbackDetailActivity.class).putExtra("id", list.get(position).getId())));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderFeedback extends RecyclerView.ViewHolder {
        TextView tvTitle, tvType, tvPriority, tvStatus, tvOpenDate;

        public ViewHolderFeedback(View itemView) {
            super(itemView);
            tvPriority = itemView.findViewById(R.id.tv_priority);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvType = itemView.findViewById(R.id.tv_type);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvOpenDate = itemView.findViewById(R.id.tv_open_date);
        }
    }
}
