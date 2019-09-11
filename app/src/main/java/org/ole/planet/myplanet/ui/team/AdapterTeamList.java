package org.ole.planet.myplanet.ui.team;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

import io.realm.Realm;

public class AdapterTeamList extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private RealmUserModel user;
    private Context context;
    private List<RealmMyTeam> list;
    private Realm mRealm;
    private String type = "";


    public AdapterTeamList(Context context, List<RealmMyTeam> list, Realm mRealm) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;
        this.user = new UserProfileDbHandler(context).getUserModel();
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_team_list, parent, false);
        return new ViewHolderTeam(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderTeam) {
            ((ViewHolderTeam) holder).created.setText(TimeUtils.getFormatedDate(list.get(position).getCreatedDate()));
            ((ViewHolderTeam) holder).type.setText(list.get(position).getTeamType());
            ((ViewHolderTeam) holder).type.setVisibility(type == null ? View.VISIBLE : View.GONE);
            ((ViewHolderTeam) holder).name.setText(list.get(position).getName());
            boolean isMyTeam = list.get(position).isMyTeam(user.getId(), mRealm);
            showActionButton(isMyTeam, holder, position);


            holder.itemView.setOnClickListener(view -> {
                if (context instanceof OnHomeItemClickListener) {
                    TeamDetailFragment f = new TeamDetailFragment();
                    Bundle b = new Bundle();
                    b.putString("id", list.get(position).getId());
                    b.putBoolean("isMyTeam", isMyTeam);
                    f.setArguments(b);
                    ((OnHomeItemClickListener) context).openCallFragment(f);
                }
            });
        }
    }

    private void showActionButton(boolean isMyTeam, RecyclerView.ViewHolder holder, int position) {
        if (isMyTeam) {
            ((ViewHolderTeam) holder).action.setText("Leave");
            ((ViewHolderTeam) holder).action.setOnClickListener(view -> {
                list.get(position).leave(user, mRealm);
                notifyDataSetChanged();
            });
        } else if (list.get(position).requested(user.getId(), mRealm)) {
            ((ViewHolderTeam) holder).action.setText("Requested");
            ((ViewHolderTeam) holder).action.setEnabled(false);
        } else {
            ((ViewHolderTeam) holder).action.setText("Request to Join");
            ((ViewHolderTeam) holder).action.setOnClickListener(view -> {
                RealmMyTeam.requestToJoin(list.get(position).getId(), user, mRealm);
                notifyDataSetChanged();
            });
        }
    }


    @Override
    public int getItemCount() {
        return list.size();
    }

    public void setType(String type) {
        this.type = type;
    }


    class ViewHolderTeam extends RecyclerView.ViewHolder {
        TextView name, created, type;
        Button action;

        public ViewHolderTeam(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            created = itemView.findViewById(R.id.created);
            type = itemView.findViewById(R.id.type);
            action = itemView.findViewById(R.id.join_leave);
        }
    }
}
