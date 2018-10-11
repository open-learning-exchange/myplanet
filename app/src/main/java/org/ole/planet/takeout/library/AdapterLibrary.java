package org.ole.planet.takeout.library;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.OnLibraryItemSelected;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;

public class AdapterLibrary extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<realm_myLibrary> libraryList;
    private List<realm_myLibrary> selectedItems;
    private OnLibraryItemSelected listener;
    private ChipCloudConfig config;

    public AdapterLibrary(Context context, List<realm_myLibrary> libraryList) {
        this.context = context;
        this.libraryList = libraryList;
        this.selectedItems = new ArrayList<>();

        config = new ChipCloudConfig()
                .selectMode(ChipCloud.SelectMode.multi)
                .useInsetPadding(true)
                .checkedChipColor(Color.parseColor("#e0e0e0"))
                .checkedTextColor(Color.parseColor("#000000"))
                .uncheckedChipColor(Color.parseColor("#e0e0e0"))
                .uncheckedTextColor(Color.parseColor("#000000"));
    }

    public void setLibraryList(List<realm_myLibrary> libraryList) {
        this.libraryList = libraryList;
        notifyDataSetChanged();
    }

    public void setListener(OnLibraryItemSelected listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_library, parent, false);
        return new ViewHolderLibrary(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ViewHolderLibrary) {
            ((ViewHolderLibrary) holder).title.setText((position + 1) + ". " + libraryList.get(position).getTitle());
            ((ViewHolderLibrary) holder).desc.setText(libraryList.get(position).getDescription());
            ((ViewHolderLibrary) holder).times_rated.setText(libraryList.get(position).getTimesRated() + " Total");
            ((ViewHolderLibrary) holder).checkBox.setChecked(selectedItems.contains(libraryList.get(position)));
            ((ViewHolderLibrary) holder).rating.setText(TextUtils.isEmpty(libraryList.get(position).getAverageRating()) ? "0.0" : String.format("%.1f", Double.parseDouble(libraryList.get(position).getAverageRating())));
            displayTagCloud(((ViewHolderLibrary) holder).flexboxDrawable, position);
        }
    }

    private void displayTagCloud(FlexboxLayout flexboxDrawable, int position) {
        flexboxDrawable.removeAllViews();
        ChipCloud chipCloud = new ChipCloud(context, flexboxDrawable, config);

        for (String s : libraryList.get(position).getTag()) {
            chipCloud.addChip(s);
        }

    }

    @Override
    public int getItemCount() {
        return libraryList.size();
    }

    class ViewHolderLibrary extends RecyclerView.ViewHolder {
        TextView title, desc, rating, times_rated;
        CheckBox checkBox;
        FlexboxLayout flexboxDrawable;

        public ViewHolderLibrary(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            rating = itemView.findViewById(R.id.rating);
            times_rated = itemView.findViewById(R.id.times_rated);
            checkBox = itemView.findViewById(R.id.checkbox);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (listener != null) {
                        Utilities.handleCheck(b, getAdapterPosition(), (ArrayList) selectedItems, libraryList);
                        listener.onSelectedListChange(selectedItems);
                    }
                }
            });
            flexboxDrawable = itemView.findViewById(R.id.flexbox_drawable);
        }
    }
}
