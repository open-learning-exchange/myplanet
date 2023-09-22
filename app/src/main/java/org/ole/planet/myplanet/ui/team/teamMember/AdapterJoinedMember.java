package org.ole.planet.myplanet.ui.team.teamMember;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ItemTitleDescBinding;
import org.ole.planet.myplanet.databinding.RowJoinedUserBinding;
import org.ole.planet.myplanet.databinding.RowOtherInfoBinding;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.userprofile.AdapterOtherInfo;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;
import io.realm.Realm;

public class AdapterJoinedMember extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private RowJoinedUserBinding rowJoinedUserBinding;
    private Context context;
    private List<RealmUserModel> list;
    private Realm mRealm;
    private String teamId;
    private RealmUserModel currentUser;
    private String teamLeaderId;

    public AdapterJoinedMember(Context context, List<RealmUserModel> list, Realm mRealm, String teamId) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;
        this.teamId = teamId;
        this.currentUser = new UserProfileDbHandler(context).getUserModel();
        RealmMyTeam leaderTeam = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("isLeader", true).findFirst();
        if (leaderTeam != null) {
            this.teamLeaderId = leaderTeam.getUserId();
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowJoinedUserBinding = RowJoinedUserBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderUser(rowJoinedUserBinding);
//        View v = LayoutInflater.from(context).inflate(R.layout.row_joined_user, parent, false);
//        return new ViewHolderUser(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderUser) {
            String[] overflowMenuOptions;
            if (list.get(position).toString().equals(" ")) {
                rowJoinedUserBinding.tvTitle.setText(list.get(position).getName());
            } else {
                rowJoinedUserBinding.tvTitle.setText(list.get(position).toString());
            }
            rowJoinedUserBinding.tvDescription.setText(list.get(position).getRoleAsString() + " (" + RealmTeamLog.getVisitCount(mRealm, list.get(position).getName(), teamId) + " " + context.getString(R.string.visits) + " )");
            Picasso.get()
                    .load(list.get(position).getUserImage())
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(rowJoinedUserBinding.memberImage);
            boolean isLoggedInUserTeamLeader = this.teamLeaderId != null && this.teamLeaderId.equals(this.currentUser.getId());

            // If the current user card is the logged in user/team leader
            if (this.teamLeaderId.equals(list.get(position).getId())) {
                rowJoinedUserBinding.tvIsLeader.setVisibility(View.VISIBLE);
                rowJoinedUserBinding.tvIsLeader.setText("("+ R.string.team_leader +")");
            } else {
                rowJoinedUserBinding.tvIsLeader.setVisibility(View.GONE);
                overflowMenuOptions = new String[]{context.getString(R.string.remove), context.getString(R.string.make_leader)};
                checkUserAndShowOverflowMenu((ViewHolderUser) holder, position, overflowMenuOptions, isLoggedInUserTeamLeader);
            }
        }
    }

    private void checkUserAndShowOverflowMenu(@NonNull ViewHolderUser holder, int position, String[] overflowMenuOptions, boolean isLoggedInUserTeamLeader) {
        /**
         * Checks if the user that is currently logged-in is the leader
         * of the team we are looking at and shows/hides the overflow menu accordingly.
         */
        if (isLoggedInUserTeamLeader) {
            rowJoinedUserBinding.icMore.setVisibility(View.VISIBLE);
            rowJoinedUserBinding.icMore.setOnClickListener(view -> {
                new AlertDialog.Builder(context).setItems(overflowMenuOptions, (dialogInterface, i) -> {
                    if (i == 0) {
                        reject(list.get(position), position);
                    } else {
                        makeLeader(list.get(position), position);
                    }
                }).setNegativeButton(R.string.dismiss, null).show();
            });
        } else {
            rowJoinedUserBinding.icMore.setVisibility(View.GONE);
        }
    }

    private void makeLeader(RealmUserModel userModel, int position) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmMyTeam team = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("userId", userModel.getId()).findFirst();
        RealmMyTeam teamLeader = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("isLeader", true).findFirst();
        if (teamLeader != null) {
            teamLeader.setLeader(false);
        }
        if (team != null) {
            team.setLeader(true);
        }

        mRealm.commitTransaction();
        notifyDataSetChanged();
        Utilities.toast(context, context.getString(R.string.leader_selected));
    }

    private void reject(RealmUserModel userModel, int position) {
        if (!mRealm.isInTransaction()) mRealm.beginTransaction();
        RealmMyTeam team = mRealm.where(RealmMyTeam.class).equalTo("teamId", teamId).equalTo("userId", userModel.getId()).findFirst();
        if (team != null) {
            team.deleteFromRealm();
        }
        mRealm.commitTransaction();
        list.remove(position);
        notifyDataSetChanged();
        Utilities.toast(context, context.getString(R.string.user_removed_from_team));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderUser extends RecyclerView.ViewHolder {
        public RowJoinedUserBinding rowJoinedUserBinding;

        public ViewHolderUser(RowJoinedUserBinding rowJoinedUserBinding) {
            super(rowJoinedUserBinding.getRoot());
            this.rowJoinedUserBinding = rowJoinedUserBinding;
        }
    }
}