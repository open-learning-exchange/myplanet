package org.ole.planet.myplanet.ui.team;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class AdapterTeam extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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
        if (context instanceof OnUserSelectedListener)
            listener = (OnUserSelectedListener) context;
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_team_grid, parent, false);
        return new ViewHolderTeam(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderTeam) {

            ((ViewHolderTeam) holder).title.setText(list.get(position).getName());
            holder.itemView.setOnClickListener(view -> {
                if (this.teamSelectedListener != null)
                    this.teamSelectedListener.onSelectedTeam(list.get(position));
                else
                    showUserList(list.get(position));
            });
        }
    }

    private void showUserList(RealmMyTeam realmMyTeam) {
        View view = LayoutInflater.from(context).inflate(R.layout.layout_user_list, null);
        EditText etSearch = view.findViewById(R.id.et_search);
        ListView lv = view.findViewById(R.id.list_user);
        users = RealmMyTeam.getUsers(realmMyTeam.get_id(), mRealm, "");
        setListAdapter(lv, users);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                users = RealmMyTeam.filterUsers(realmMyTeam.get_id(), charSequence.toString(), mRealm);
                setListAdapter(lv, users);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        lv.setOnItemClickListener((adapterView, view1, i, l) -> {
            if (listener != null)
                listener.onSelectedUser(users.get(i));
        });
        new AlertDialog.Builder(context).setTitle("Select User To Login").setView(view).setNegativeButton("Dismiss", null).show();
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

    class ViewHolderTeam extends RecyclerView.ViewHolder {
        TextView title;

        public ViewHolderTeam(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
        }
    }
}
