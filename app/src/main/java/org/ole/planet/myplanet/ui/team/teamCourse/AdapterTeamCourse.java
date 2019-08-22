package org.ole.planet.myplanet.ui.team.teamCourse;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;

import java.util.List;

import io.realm.Realm;

public class AdapterTeamCourse extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmMyCourse> list;
    private OnHomeItemClickListener listener;

    public AdapterTeamCourse(Context context, List<RealmMyCourse> list) {
        this.context = context;
        this.list = list;
        if (context instanceof OnHomeItemClickListener) {
            listener = (OnHomeItemClickListener) context;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_team_resource, parent, false);
        return new ViewHolderTeamCourse(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderTeamCourse) {
            ((ViewHolderTeamCourse) holder).title.setText(list.get(position).getCourseTitle());
            ((ViewHolderTeamCourse) holder).description.setText(list.get(position).getDescription());
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    Fragment f = new TakeCourseFragment();
                    Bundle b = new Bundle();
                    b.putString("id", list.get(position).getCourseId());
                    f.setArguments(b);
                    listener.openCallFragment(f);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderTeamCourse extends RecyclerView.ViewHolder {
        TextView title, description;

        public ViewHolderTeamCourse(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            description = itemView.findViewById(R.id.description);
        }
    }
}
