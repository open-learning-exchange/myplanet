package org.ole.planet.myplanet.ui.team;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.ItemTeamGridBinding;
import org.ole.planet.myplanet.databinding.LayoutUserListBinding;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class AdapterTeam extends RecyclerView.Adapter<AdapterTeam.ViewHolderTeam> {
    private ItemTeamGridBinding itemTeamGridBinding;
    private Context context;
    private List<RealmMyTeam> list;
    private Realm mRealm;
    private OnUserSelectedListener listener;
    private List<RealmUserModel> users;
    private OnTeamSelectedListener teamSelectedListener;

    public interface OnTeamSelectedListener {
        void onSelectedTeam(RealmMyTeam team);
    }

    public void setTeamSelectedListener(OnTeamSelectedListener teamSelectedListener) {
        this.teamSelectedListener = teamSelectedListener;
        Utilities.log("Team selected listener " + (teamSelectedListener == null));
    }

    public AdapterTeam(Context context, List<RealmMyTeam> list, Realm mRealm) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;

        if (context instanceof OnUserSelectedListener) listener = (OnUserSelectedListener) context;
    }

    @NonNull
    @Override
    public ViewHolderTeam onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        itemTeamGridBinding = ItemTeamGridBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderTeam(itemTeamGridBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderTeam holder, int position) {
        itemTeamGridBinding.title.setText(list.get(position).getName());
        holder.itemView.setOnClickListener(view -> {
            if (this.teamSelectedListener != null)
                this.teamSelectedListener.onSelectedTeam(list.get(position));
            else showUserList(list.get(position));
        });
    }

    private void showUserList(RealmMyTeam realmMyTeam) {
        LayoutUserListBinding layoutUserListBinding = LayoutUserListBinding.inflate(LayoutInflater.from(context));
        users = RealmMyTeam.getUsers(realmMyTeam.get_id(), mRealm, "");
        setListAdapter(layoutUserListBinding.listUser, users);
        layoutUserListBinding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                users = RealmMyTeam.filterUsers(realmMyTeam.get_id(), charSequence.toString(), mRealm);
                setListAdapter(layoutUserListBinding.listUser, users);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        layoutUserListBinding.listUser.setOnItemClickListener((adapterView, view1, i, l) -> {
            if (listener != null) listener.onSelectedUser(users.get(i));
        });
        new AlertDialog.Builder(context).setTitle(R.string.select_user_to_login).setView(layoutUserListBinding.getRoot()).setNegativeButton(R.string.dismiss, null).show();
    }

    private void setListAdapter(ListView lv, List<RealmUserModel> users) {
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, users);
        lv.setAdapter(adapter);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public interface OnUserSelectedListener {
        void onSelectedUser(RealmUserModel userModel);
    }

    static class ViewHolderTeam extends RecyclerView.ViewHolder {
        ItemTeamGridBinding itemTeamGridBinding;

        public ViewHolderTeam(ItemTeamGridBinding itemTeamGridBinding) {
            super(itemTeamGridBinding.getRoot());
            this.itemTeamGridBinding = itemTeamGridBinding;
        }
    }
}
