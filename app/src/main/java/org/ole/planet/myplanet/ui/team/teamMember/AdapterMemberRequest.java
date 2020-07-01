package org.ole.planet.myplanet.ui.team.teamMember;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;

import java.util.List;

import io.realm.Realm;

public class AdapterMemberRequest extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmUserModel> list;
    private Realm mRealm;
    private String teamId;

    public AdapterMemberRequest(Context context, List<RealmUserModel> list, Realm mRealm) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_member_request, parent, false);
        return new ViewHolderUser(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderUser) {
            ((ViewHolderUser) holder).name.setText(list.get(position).toString());
            ((ViewHolderUser) holder).buttonAccept.setOnClickListener(view -> {
                acceptReject(list.get(position), true, position);

            });
            ((ViewHolderUser) holder).buttonReject.setOnClickListener(view -> acceptReject(list.get(position), false, position));
        }
    }

    private void acceptReject(RealmUserModel userModel, boolean isAccept, int position) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmMyTeam team = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("userId", userModel.getId()).findFirst();
        if (team != null) {
            if (isAccept) {
                team.setDocType("membership");
                team.setUpdated(true);
            } else {
                team.deleteFromRealm();
            }
        }
        mRealm.commitTransaction();
        list.remove(position);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderUser extends RecyclerView.ViewHolder {
        TextView name;
        Button buttonAccept, buttonReject;

        public ViewHolderUser(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_name);
            buttonAccept = itemView.findViewById(R.id.btn_accept);
            buttonReject = itemView.findViewById(R.id.btn_reject);
        }
    }
}
