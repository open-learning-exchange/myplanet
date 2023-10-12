package org.ole.planet.myplanet.ui.team.teamResource;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.databinding.RowTeamResourceBinding;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmMyTeam;

import java.util.List;

import io.realm.Realm;

public class AdapterTeamResource extends RecyclerView.Adapter<AdapterTeamResource.ViewHolderTeamResource> {
    private RowTeamResourceBinding rowTeamResourceBinding;
    private Context context;
    private List<RealmMyLibrary> list;
    private Realm mRealm;
    private OnHomeItemClickListener listener;
    private SharedPreferences settings;
    private String teamCreator;

    public AdapterTeamResource(Context context, List<RealmMyLibrary> list, Realm mRealm, String teamId, SharedPreferences settings) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;
        this.settings = settings;
        teamCreator = RealmMyTeam.getTeamCreator(teamId, mRealm);
        if (context instanceof OnHomeItemClickListener) {
            listener = (OnHomeItemClickListener) context;
        }
    }

    @NonNull
    @Override
    public ViewHolderTeamResource onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowTeamResourceBinding = RowTeamResourceBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderTeamResource(rowTeamResourceBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderTeamResource holder, int position) {
        rowTeamResourceBinding.tvTitle.setText(list.get(position).getTitle());
        rowTeamResourceBinding.tvDescription.setText(list.get(position).getDescription());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.openLibraryDetailFragment(list.get(position));
            }
        });
        rowTeamResourceBinding.ivRemove.setOnClickListener(view -> {
        });
        if (!settings.getString("userId", "--").equalsIgnoreCase(teamCreator)) {
            rowTeamResourceBinding.ivRemove.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolderTeamResource extends RecyclerView.ViewHolder {
        RowTeamResourceBinding rowTeamResourceBinding;

        public ViewHolderTeamResource(RowTeamResourceBinding rowTeamResourceBinding) {
            super(rowTeamResourceBinding.getRoot());
            this.rowTeamResourceBinding = rowTeamResourceBinding;
        }
    }
}
