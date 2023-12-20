package org.ole.planet.myplanet.ui.team.teamTask;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.RowTaskBinding;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class AdapterTask extends RecyclerView.Adapter<AdapterTask.ViewHolderTask> {
    private RowTaskBinding rowTaskBinding;
    private Context context;
    private List<RealmTeamTask> list;
    private OnCompletedListener listener;
    private Realm realm;

    public AdapterTask(Context context, Realm mRealm, List<RealmTeamTask> list) {
        this.context = context;
        this.list = list;
        this.realm = mRealm;
    }

    public void setListener(OnCompletedListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolderTask onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowTaskBinding = RowTaskBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderTask(rowTaskBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderTask holder, int position) {
        rowTaskBinding.checkbox.setText(list.get(position).getTitle());
        rowTaskBinding.checkbox.setChecked(list.get(position).isCompleted());
        Utilities.log(list.get(position).getDeadline() + "");
        rowTaskBinding.deadline.setText(context.getString(R.string.deadline_colon) + TimeUtils.formatDate(list.get(position).getDeadline()) + (list.get(position).isCompleted() ? context.getString(R.string.completed_colon) + TimeUtils.formatDate(list.get(position).getCompletedTime()) : ""));
        showAssignee(holder, list.get(position));
        rowTaskBinding.checkbox.setOnCheckedChangeListener((compoundButton, b) -> {
            if (listener != null) listener.onCheckChange(list.get(position), b);
        });
        rowTaskBinding.icMore.setOnClickListener(view -> {
            if (listener != null) listener.onClickMore(list.get(position));
        });
        rowTaskBinding.editTask.setOnClickListener(view -> {
            if (listener != null) listener.onEdit(list.get(position));
        });
        rowTaskBinding.deleteTask.setOnClickListener(view -> {
            if (listener != null) listener.onDelete(list.get(position));
        });
        holder.itemView.setOnClickListener(view -> DialogUtils.showCloseAlert(context, list.get(position).getTitle(), list.get(position).getDescription()));
    }

    private void showAssignee(RecyclerView.ViewHolder holder, RealmTeamTask realmTeamTask) {
        if (!TextUtils.isEmpty(realmTeamTask.getAssignee())) {
            RealmUserModel model = realm.where(RealmUserModel.class).equalTo("id", realmTeamTask.getAssignee()).findFirst();
            if (model != null) {
                rowTaskBinding.assignee.setText(context.getString(R.string.assigned_to_colon) + model.name);
            }
        } else {
            rowTaskBinding.assignee.setText(R.string.no_assignee);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    interface OnCompletedListener {
        void onCheckChange(RealmTeamTask realmTeamTask, boolean b);

        void onEdit(RealmTeamTask task);

        void onDelete(RealmTeamTask task);

        void onClickMore(RealmTeamTask realmTeamTask);
    }

    static class ViewHolderTask extends RecyclerView.ViewHolder {
        RowTaskBinding rowTaskBinding;

        public ViewHolderTask(RowTaskBinding rowTaskBinding) {
            super(rowTaskBinding.getRoot());
            this.rowTaskBinding = rowTaskBinding;
        }
    }
}