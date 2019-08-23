package org.ole.planet.myplanet.ui.myhealth;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.ui.userprofile.AdapterOtherInfo;

import java.util.List;

public class AdapterVitalSign extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmVitalSign> list;

    public AdapterVitalSign(Context context, List<RealmVitalSign> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_vital_sign, parent, false);
        return new AdapterOtherInfo.ViewHolderOtherInfo(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AdapterOtherInfo.ViewHolderOtherInfo) {
            ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvTitle.setText(list.get(position).getUserId());
            String desc = "";
            desc += list.get(position).getBodyTemp() > 99 ? "Body temperature is high\n" : "Body temperature is normal\n";
            ((AdapterOtherInfo.ViewHolderOtherInfo) holder).tvDescription.setText(desc);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

//    class ViewHolderVitalSign extends RecyclerView.ViewHolder {
//        TextView username, description;
//
//        public ViewHolderVitalSign(View itemView) {
//            super(itemView);
//            username = itemView.findViewById(R.id.useranme);
//            description = itemView.findViewById(R.id.description);
//        }
//    }
}
