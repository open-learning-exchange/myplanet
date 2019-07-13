package org.ole.planet.myplanet.ui.team;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyTeam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.sync.LoginActivity;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

import io.realm.Realm;

public class AdapterTeamList extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmMyTeam> list;
    private Realm mRealm;


    public AdapterTeamList(Context context, List<RealmMyTeam> list, Realm mRealm) {
        this.context = context;
        this.list = list;
        this.mRealm = mRealm;
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_team_list, parent, false);
        return new ViewHolderTeam(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderTeam) {
            ((ViewHolderTeam) holder).name.setText(list.get(position).getName());
            ((ViewHolderTeam) holder).created.setText(TimeUtils.getFormatedDate(list.get(position).getCreatedDate()));
            ((ViewHolderTeam) holder).type.setText(list.get(position).getTeamType());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                }
            });
        }
    }


    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderTeam extends RecyclerView.ViewHolder {
        TextView name, created, type;
        Button action;

        public ViewHolderTeam(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.name);
            created = itemView.findViewById(R.id.created);
            type = itemView.findViewById(R.id.type);
            action = itemView.findViewById(R.id.join_leave);
        }
    }
}
