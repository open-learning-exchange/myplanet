package org.ole.planet.myplanet.ui.feedback;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.RowFeedbackBinding;
import org.ole.planet.myplanet.model.RealmFeedback;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

import io.realm.RealmResults;

public class AdapterFeedback extends RecyclerView.Adapter<AdapterFeedback.ViewHolderFeedback> {
    private RowFeedbackBinding rowFeedbackBinding;
    private Context context;
    private List<RealmFeedback> list;

    public AdapterFeedback(Context context, List<RealmFeedback> list) {
        this.context = context;
        this.list = list;
    }

    public void updateData(RealmResults<RealmFeedback> newData) {
        list = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolderFeedback onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowFeedbackBinding = RowFeedbackBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderFeedback(rowFeedbackBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderFeedback holder, int position) {
        rowFeedbackBinding.tvTitle.setText(list.get(position).getTitle());
        rowFeedbackBinding.tvType.setText(list.get(position).getType());
        rowFeedbackBinding.tvPriority.setText(list.get(position).getPriority());
        rowFeedbackBinding.tvStatus.setText(list.get(position).getStatus());
        if ("yes".equalsIgnoreCase(list.get(position).getPriority()))
            rowFeedbackBinding.tvPriority.setBackground(context.getResources().getDrawable(R.drawable.bg_primary));
        else
            rowFeedbackBinding.tvPriority.setBackground(context.getResources().getDrawable(R.drawable.bg_grey));
        rowFeedbackBinding.tvStatus.setBackground(context.getResources().getDrawable("open".equalsIgnoreCase(list.get(position).getStatus()) ? R.drawable.bg_primary : R.drawable.bg_grey));
        rowFeedbackBinding.tvOpenDate.setText(TimeUtils.getFormatedDate(Long.parseLong(list.get(position).getOpenTime())));
        rowFeedbackBinding.getRoot().setOnClickListener(v -> context.startActivity(new Intent(context, FeedbackDetailActivity.class).putExtra("id", list.get(position).getId())));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolderFeedback extends RecyclerView.ViewHolder {
        RowFeedbackBinding rowFeedbackBinding;

        public ViewHolderFeedback(RowFeedbackBinding rowFeedbackBinding) {
            super(rowFeedbackBinding.getRoot());
            this.rowFeedbackBinding = rowFeedbackBinding;
        }
    }
}
