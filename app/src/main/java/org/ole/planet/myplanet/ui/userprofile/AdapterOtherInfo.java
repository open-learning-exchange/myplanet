package org.ole.planet.myplanet.ui.userprofile;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.databinding.RowOtherInfoBinding;
import org.ole.planet.myplanet.utilities.JsonUtils;

import java.util.List;

public class AdapterOtherInfo extends RecyclerView.Adapter<AdapterOtherInfo.ViewHolderOtherInfo> {
    private RowOtherInfoBinding rowOtherInfoBinding;
    private Context context;
    private List<String> list;

    public AdapterOtherInfo(Context context, List<String> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolderOtherInfo onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowOtherInfoBinding = RowOtherInfoBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderOtherInfo(rowOtherInfoBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderOtherInfo holder, int position) {
        if (position < list.size()) {
            String jsonString = list.get(position);
            JsonObject object = new Gson().fromJson(jsonString, JsonObject.class);
            String res = JsonUtils.getString("name", object) + "\n" +
                    JsonUtils.getString("relationship", object) + "\n" +
                    JsonUtils.getString("phone", object) + "\n" +
                    JsonUtils.getString("email", object) + "\n";
            holder.rowOtherInfoBinding.tvDescription.setText(res);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolderOtherInfo extends RecyclerView.ViewHolder {
        public RowOtherInfoBinding rowOtherInfoBinding;

        public ViewHolderOtherInfo(RowOtherInfoBinding rowOtherInfoBinding) {
            super(rowOtherInfoBinding.getRoot());
            this.rowOtherInfoBinding = rowOtherInfoBinding;
        }
    }
}
