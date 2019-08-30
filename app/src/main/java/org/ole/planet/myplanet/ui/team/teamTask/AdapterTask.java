package org.ole.planet.myplanet.ui.team.teamTask;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmTeamTask;

import java.util.List;

import io.realm.Realm;

public class AdapterTask extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<RealmTeamTask> list;
    private OnCompletedListener listener;
    interface OnCompletedListener{
       void onCheckChange(RealmTeamTask realmTeamTask, boolean b);
    }

    public AdapterTask(Context context, List<RealmTeamTask> list) {
        this.context = context;
        this.list = list;

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
            ((ViewHolderTask) holder).completed.setText(list.get(position).getTitle());
            ((ViewHolderTask) holder).completed.setChecked(list.get(position).isCompleted());
            ((ViewHolderTask) holder).deadline.setText("Deadline : " + list.get(position).getDeadline());
            ((ViewHolderTask) holder).completed.setOnCheckedChangeListener((compoundButton, b) -> {
                if (listener!=null)
                    listener.onCheckChange(list.get(position), b);
            });
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderTask extends RecyclerView.ViewHolder {
        CheckBox completed;
        TextView deadline;

        public ViewHolderTask(View itemView) {
            super(itemView);
            completed = itemView.findViewById(R.id.checkbox);
            deadline = itemView.findViewById(R.id.deadline);
        }
    }
}
