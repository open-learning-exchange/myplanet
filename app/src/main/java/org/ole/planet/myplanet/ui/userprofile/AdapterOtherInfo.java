package org.ole.planet.myplanet.ui.userprofile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.JsonUtils;

import java.util.List;

public class AdapterOtherInfo extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<String> list;

    public AdapterOtherInfo(Context context, List<String> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_other_info, parent, false);
        return new ViewHolderOtherInfo(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderOtherInfo) {
            JsonObject object = new Gson().fromJson(list.get(position), JsonObject.class);
            String res = JsonUtils.getString("name", object) + "\n" +
                    JsonUtils.getString("relationship", object) + "\n" +
                    JsonUtils.getString("phone", object) + "\n" +
                    JsonUtils.getString("email", object) + "\n";
            ((ViewHolderOtherInfo) holder).tvDescription.setText(res);
//            ((ViewHolderOtherInfo) holder).tvDescription.setText(JsonUtils.getString("description", object));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolderOtherInfo extends RecyclerView.ViewHolder {
       public TextView tvTitle, tvDescription;

        public ViewHolderOtherInfo(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDescription = itemView.findViewById(R.id.tv_description);
          //  tvTitle.setVisibility(View.GONE);
        }
    }
}
