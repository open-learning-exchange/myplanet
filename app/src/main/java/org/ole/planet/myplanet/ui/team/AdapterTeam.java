package org.ole.planet.myplanet.ui.team;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;

import java.util.List;

public class AdapterTeam extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    Context context;
    List<RealmMyTeam> list;

    public AdapterTeam(Context context, List<RealmMyTeam> list) {
        this.context = context;
        this.list = list;
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_team_grid, parent, false);
        return new ViewHolderTeam(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderTeam){
            ((ViewHolderTeam) holder).title.setText(list.get(position).getName());
        }
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
