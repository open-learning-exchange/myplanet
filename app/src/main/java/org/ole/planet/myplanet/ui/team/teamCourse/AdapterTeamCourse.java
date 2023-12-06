package org.ole.planet.myplanet.ui.team.teamCourse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;

import java.util.List;

import io.realm.Realm;

public class AdapterTeamCourse extends RecyclerView.Adapter<AdapterTeamCourse.ViewHolderTeamCourse> {
    private RowTeamResourceBinding rowTeamResourceBinding;
    private Context context;
    private List<RealmMyCourse> list;
    private OnHomeItemClickListener listener;
    private SharedPreferences settings;
    private String teamCreator;

    public AdapterTeamCourse(Context context, List<RealmMyCourse> list, Realm mRealm, String teamId, SharedPreferences settings) {
        this.context = context;
        this.list = list;
        if (context instanceof OnHomeItemClickListener) {
            listener = (OnHomeItemClickListener) context;
        }
        this.settings = settings;
        teamCreator = RealmMyTeam.getTeamCreator(teamId, mRealm);
    }

    @NonNull
    @Override
    public ViewHolderTeamCourse onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowTeamResourceBinding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderTeamCourse(rowTeamResourceBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderTeamCourse holder, int position) {
        rowTeamResourceBinding.tvTitle.setText(list.get(position).courseTitle);
        rowTeamResourceBinding.tvDescription.setText(list.get(position).description);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                Bundle b = new Bundle();
                b.putString("id", list.get(position).courseId);
                listener.openCallFragment(TakeCourseFragment.newInstance(b));
            }
        });
        if (!settings.getString("userId", "--").equalsIgnoreCase(teamCreator)) {
            holder.itemView.findViewById(R.id.iv_remove).setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolderTeamCourse extends RecyclerView.ViewHolder {
        public RowTeamResourceBinding rowTeamResourceBinding;

        public ViewHolderTeamCourse(RowTeamResourceBinding rowTeamResourceBinding) {
            super(rowTeamResourceBinding.getRoot());
            this.rowTeamResourceBinding = rowTeamResourceBinding;
        }
    }
}
