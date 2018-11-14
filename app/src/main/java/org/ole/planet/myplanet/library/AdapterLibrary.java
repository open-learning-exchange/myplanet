package org.ole.planet.myplanet.library;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatRatingBar;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnLibraryItemSelected;
import org.ole.planet.myplanet.courses.TakeCourseFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipListener;
import io.realm.Realm;

public class AdapterLibrary extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<realm_myLibrary> libraryList;
    private List<realm_myLibrary> selectedItems;
    private OnLibraryItemSelected listener;
    private ChipCloudConfig config;
    private OnHomeItemClickListener homeItemClickListener;
    private HashMap<String , JsonObject> ratingMap;
    public AdapterLibrary(Context context, List<realm_myLibrary> libraryList, HashMap<String, JsonObject> ratingMap) {
        this.ratingMap = ratingMap;
        this.context = context;
        this.libraryList = libraryList;
        this.selectedItems = new ArrayList<>();

        config = Utilities.getCloudConfig()
                .selectMode(ChipCloud.SelectMode.single);
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }

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
            ((ViewHolderLibrary) holder).timesRated.setText(libraryList.get(position).getTimesRated() + " Total");
            ((ViewHolderLibrary) holder).checkBox.setChecked(selectedItems.contains(libraryList.get(position)));
            ((ViewHolderLibrary) holder).rating.setText(TextUtils.isEmpty(libraryList.get(position).getAverageRating()) ? "0.0" : String.format("%.1f", Double.parseDouble(libraryList.get(position).getAverageRating())));
            displayTagCloud(((ViewHolderLibrary) holder).flexboxDrawable, position);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openLibrary(libraryList.get(position));
                }
            });
            ((ViewHolderLibrary) holder).llRating.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    homeItemClickListener.showRatingDialog("resource", libraryList.get(position).getResource_id(), libraryList.get(position).getTitle());
                }
            });
            if (ratingMap.containsKey(libraryList.get(position).getResource_id())){
                JsonObject object = ratingMap.get(libraryList.get(position).getResource_id());
                ((ViewHolderLibrary) holder).rating.setText(object.get("averageRating").getAsFloat() + "" );
                ((ViewHolderLibrary) holder).timesRated.setText(object.get("total").getAsInt() + " total");
                ((ViewHolderLibrary) holder).ratingBar.setRating(object.get("averageRating").getAsFloat());
            }
        }
    }

    private void openLibrary(realm_myLibrary library) {
        if (homeItemClickListener != null) {

            homeItemClickListener.openLibraryDetailFragment(library);
        }
    }


    private void displayTagCloud(FlexboxLayout flexboxDrawable, int position) {
        flexboxDrawable.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(context, flexboxDrawable, config);

        for (String s : libraryList.get(position).getTag()) {
            chipCloud.addChip(s);
            chipCloud.setListener(new ChipListener() {
                @Override
                public void chipCheckedChange(int i, boolean b, boolean b1) {
                    if (b1 && listener != null) {
                        listener.onTagClicked(chipCloud.getLabel(i));
                    }
                }
            });
        }

    }

    @Override
    public int getItemCount() {
        return libraryList.size();
    }

    class ViewHolderLibrary extends RecyclerView.ViewHolder {
        TextView title, desc, rating, timesRated;
        CheckBox checkBox;
        AppCompatRatingBar ratingBar;
        FlexboxLayout flexboxDrawable;
        LinearLayout llRating;
        public ViewHolderLibrary(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            rating = itemView.findViewById(R.id.rating);
            timesRated = itemView.findViewById(R.id.times_rated);
            ratingBar = itemView.findViewById(R.id.rating_bar);
            checkBox = itemView.findViewById(R.id.checkbox);
            llRating = itemView.findViewById(R.id.ll_rating);
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
