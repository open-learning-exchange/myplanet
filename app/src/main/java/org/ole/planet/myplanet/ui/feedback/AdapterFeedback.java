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
        rowFeedbackBinding.tvTitle.setText(list.get(position).title);
        rowFeedbackBinding.tvType.setText(list.get(position).type);
        rowFeedbackBinding.tvPriority.setText(list.get(position).priority);
        rowFeedbackBinding.tvStatus.setText(list.get(position).status);
        if ("yes".equalsIgnoreCase(list.get(position).priority))
            rowFeedbackBinding.tvPriority.setBackground(context.getResources().getDrawable(R.drawable.bg_primary));
        else
            rowFeedbackBinding.tvPriority.setBackground(context.getResources().getDrawable(R.drawable.bg_grey));
        rowFeedbackBinding.tvStatus.setBackground(context.getResources().getDrawable("open".equalsIgnoreCase(list.get(position).status) ? R.drawable.bg_primary : R.drawable.bg_grey));
        rowFeedbackBinding.tvOpenDate.setText(TimeUtils.getFormatedDate(Long.parseLong(list.get(position).openTime)));
        rowFeedbackBinding.getRoot().setOnClickListener(v -> context.startActivity(new Intent(context, FeedbackDetailActivity.class).putExtra("id", list.get(position).id)));
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
