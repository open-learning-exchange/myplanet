package org.ole.planet.myplanet.ui.team.teamCourse;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.ui.course.TakeCourseFragment;
import org.ole.planet.myplanet.ui.userprofile.AdapterOtherInfo;

import java.util.List;

import io.realm.Realm;

public class AdapterTeamCourse extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_team_resource, parent, false);
        return new AdapterOtherInfo.ViewHolderOtherInfo(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AdapterOtherInfo.ViewHolderOtherInfo) {
            ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvTitle.setText(list.get(position).getCourseTitle());
            ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvDescription.setText(list.get(position).getDescription());
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    Bundle b = new Bundle();
                    b.putString("id", list.get(position).getCourseId());
                    listener.openCallFragment(TakeCourseFragment.newInstance(b));
                }
            });
            if (!settings.getString("userId", "--").equalsIgnoreCase(teamCreator)) {
                holder.itemView.findViewById(R.id.iv_remove).setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
//
//    class ViewHolderTeamCourse extends RecyclerView.ViewHolder {
//        TextView title, description;
//
//        public ViewHolderTeamCourse(View itemView) {
//            super(itemView);
//            title = itemView.findViewById(R.id.title);
//            description = itemView.findViewById(R.id.description);
//        }
//    }
}
