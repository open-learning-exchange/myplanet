package org.ole.planet.myplanet.ui.team;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmUserModel;

import java.util.List;

import io.realm.Realm;

public class AdapterJoinedMemeber extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmUserModel> list;
    private Realm mRealm;

    public AdapterJoinedMemeber(Context context, List<RealmUserModel> list, Realm mRealm) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View   v = LayoutInflater.from(context).inflate(R.layout.row_joined_user, parent, false);
        return new ViewHolderUser(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderUser){
            ((ViewHolderUser) holder).name.setText(list.get(position).getName());
            ((ViewHolderUser) holder).visit.setText(list.get(position).getRoleAsString() + " (" + RealmTeamLog.getVisitCount(mRealm, list.get(position).getName()) + " visits )");
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
    class ViewHolderUser extends RecyclerView.ViewHolder{
        TextView name, visit;
        public ViewHolderUser(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_name);
            visit = itemView.findViewById(R.id.tv_visits);
        }
    }
}
