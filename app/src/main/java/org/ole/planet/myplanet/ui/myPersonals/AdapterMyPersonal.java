package org.ole.planet.myplanet.ui.myPersonals;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.ui.userprofile.AdapterOtherInfo;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

public class AdapterMyPersonal extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<RealmMyPersonal> list ;
    OnHomeItemClickListener clickListener;
    public AdapterMyPersonal(Context context, List<RealmMyPersonal> list) {
        this.context = context;
        this.list = list;
        if (context instanceof OnHomeItemClickListener)
            clickListener = (OnHomeItemClickListener) context;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_my_personal, parent, false);
        return new ViewHolderMyPersonal(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderMyPersonal){
            ((ViewHolderMyPersonal) holder).title.setText(list.get(position).getTitle());
            ((ViewHolderMyPersonal) holder).description.setText(list.get(position).getDescription());
            ((ViewHolderMyPersonal) holder).date.setText(TimeUtils.getFormatedDate(list.get(position).getDate()));
            holder.itemView.setOnClickListener(view -> {
//                    if (clickListener!=null)
//                        clickListener.openResource(list.get(position).getPath());
            });
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderMyPersonal extends RecyclerView.ViewHolder{
        TextView title, description, date;
        public ViewHolderMyPersonal(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            description = itemView.findViewById(R.id.description);
            date = itemView.findViewById(R.id.date);
        }
    }
}
