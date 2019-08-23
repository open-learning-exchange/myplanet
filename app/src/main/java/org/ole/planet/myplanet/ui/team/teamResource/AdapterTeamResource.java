package org.ole.planet.myplanet.ui.team.teamResource;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmTeamLog;
import org.ole.planet.myplanet.model.RealmUserModel;

import java.util.List;

import io.realm.Realm;

public class AdapterTeamResource extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmMyLibrary> list;
    private Realm mRealm;
    private OnHomeItemClickListener listener;

    public AdapterTeamResource(Context context, List<RealmMyLibrary> list, Realm mRealm) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;
        if (context instanceof OnHomeItemClickListener) {
            listener = (OnHomeItemClickListener) context;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View   v = LayoutInflater.from(context).inflate(R.layout.row_team_resource, parent, false);
        return new ViewHolderTeamResource(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderTeamResource){
            ((ViewHolderTeamResource) holder).title.setText(list.get(position).getTitle());
            ((ViewHolderTeamResource) holder).description.setText(list.get(position).getDescription());
            holder.itemView.setOnClickListener(v->{
                if (listener!=null){
                    listener.openLibraryDetailFragment(list.get(position));
                }
            });
            ((ViewHolderTeamResource) holder).ivRemove.setOnClickListener(view -> {
                // TODO: 2019-08-21 Remove resource from team
            });
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderTeamResource extends RecyclerView.ViewHolder{
        TextView title, description;
        ImageView ivRemove;
        public ViewHolderTeamResource(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            description = itemView.findViewById(R.id.description);
            ivRemove = itemView.findViewById(R.id.iv_remove);
        }
    }
}
