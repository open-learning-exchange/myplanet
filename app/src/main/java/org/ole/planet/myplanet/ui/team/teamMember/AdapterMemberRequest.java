package org.ole.planet.myplanet.ui.team.teamMember;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.databinding.RowMemberRequestBinding;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;

import java.util.List;

import io.realm.Realm;

public class AdapterMemberRequest extends RecyclerView.Adapter<AdapterMemberRequest.ViewHolderUser> {
    private RowMemberRequestBinding rowMemberRequestBinding;
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
    public ViewHolderUser onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowMemberRequestBinding = RowMemberRequestBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderUser(rowMemberRequestBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderUser holder, int position) {
        if (list.get(position).toString().equals(" ")) {
            rowMemberRequestBinding.tvName.setText(list.get(position).name);
        } else {
            rowMemberRequestBinding.tvName.setText(list.get(position).toString());
        }
        rowMemberRequestBinding.btnAccept.setOnClickListener(view -> {
            acceptReject(list.get(position), true, position);

        });
        rowMemberRequestBinding.btnReject.setOnClickListener(view -> acceptReject(list.get(position), false, position));
    }

    private void acceptReject(RealmUserModel userModel, boolean isAccept, int position) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmMyTeam team = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("userId", userModel.id).findFirst();
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

    static class ViewHolderUser extends RecyclerView.ViewHolder {
        RowMemberRequestBinding rowMemberRequestBinding;

        public ViewHolderUser(RowMemberRequestBinding rowMemberRequestBinding) {
            super(rowMemberRequestBinding.getRoot());
            this.rowMemberRequestBinding = rowMemberRequestBinding;
        }
    }
}