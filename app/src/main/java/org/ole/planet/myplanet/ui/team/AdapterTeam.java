package org.ole.planet.myplanet.ui.team;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.sync.LoginActivity;
import org.ole.planet.myplanet.utilities.LocaleHelper;

import java.util.List;

import io.realm.Realm;

public class AdapterTeam extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmMyTeam> list;
    private Realm mRealm;
    private OnUserSelectedListener listener;

    public interface OnUserSelectedListener {
        void onSelectedUser(RealmUserModel userModel);
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
                showUserList(list.get(position));
            });
        }
    }

    private void showUserList(RealmMyTeam realmMyTeam) {

        List<RealmUserModel> users = mRealm.where(RealmUserModel.class).in("id", realmMyTeam.getUserId().toArray(new String[0])).findAll();
        ArrayAdapter<RealmUserModel> adapter = new ArrayAdapter<RealmUserModel>(context, android.R.layout.simple_list_item_1, users);

        new AlertDialog.Builder(context)
                .setTitle("Select User To Login")
                .setAdapter(adapter, (dialogInterface, i) -> {
                    if (listener!=null)
                        listener.onSelectedUser(users.get(i));
                })
                .setNegativeButton("Dismiss", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderTeam extends RecyclerView.ViewHolder {
        TextView title;

        public ViewHolderTeam(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
        }
    }
}
