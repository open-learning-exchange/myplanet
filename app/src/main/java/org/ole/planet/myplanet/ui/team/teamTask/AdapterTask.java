package org.ole.planet.myplanet.ui.team.teamTask;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmTeamTask;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class AdapterTask extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_task, parent, false);
        return new ViewHolderTask(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderTask) {
            ((ViewHolderTask) holder).completed.setText(list.get(position).getTitle() );
            ((ViewHolderTask) holder).completed.setChecked(list.get(position).isCompleted());
            Utilities.log(list.get(position).getDeadline() + "");
            ((ViewHolderTask) holder).deadline.setText("Deadline : " + TimeUtils.formatDate(list.get(position).getDeadline())  + (list.get(position).isCompleted() ? "\nCompleted : " + TimeUtils.formatDate(list.get(position).getCompletedTime()) : ""));
            showAssignee(holder, list.get(position));
            ((ViewHolderTask) holder).completed.setOnCheckedChangeListener((compoundButton, b) -> {
                if (listener != null) listener.onCheckChange(list.get(position), b);
            });
            ((ViewHolderTask) holder).icMore.setOnClickListener(view -> {
                if (listener != null) listener.onClickMore(list.get(position));
            });
            ((ViewHolderTask) holder).editTask.setOnClickListener(view -> {
                if (listener != null) listener.onEdit(list.get(position));
            });
            ((ViewHolderTask) holder).deleteTask.setOnClickListener(view -> {
                if (listener != null)
                    listener.onDelete(list.get(position));
            });
            holder.itemView.setOnClickListener(view -> DialogUtils.showCloseAlert(context, list.get(position).getTitle(), list.get(position).getDescription()));
        }
    }

    private void showAssignee(RecyclerView.ViewHolder holder, RealmTeamTask realmTeamTask) {
        if (!TextUtils.isEmpty(realmTeamTask.getAssignee())) {
            RealmUserModel model = realm.where(RealmUserModel.class).equalTo("id", realmTeamTask.getAssignee()).findFirst();
            if (model != null) {
                ((ViewHolderTask) holder).assignee.setText("Assigned to : " + model.getName());
            }
        } else {
            ((ViewHolderTask) holder).assignee.setText(R.string.no_assignee);

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

    class ViewHolderTask extends RecyclerView.ViewHolder {
        CheckBox completed;
        TextView deadline, assignee;
        ImageView icMore, editTask, deleteTask;

        public ViewHolderTask(View itemView) {
            super(itemView);
            completed = itemView.findViewById(R.id.checkbox);
            deadline = itemView.findViewById(R.id.deadline);
            assignee = itemView.findViewById(R.id.assignee);
            icMore = itemView.findViewById(R.id.ic_more);
            editTask = itemView.findViewById(R.id.edit_task);
            deleteTask = itemView.findViewById(R.id.delete_task);
        }
    }
}
