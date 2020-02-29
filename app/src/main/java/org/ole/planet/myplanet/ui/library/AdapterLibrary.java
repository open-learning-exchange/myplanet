package org.ole.planet.myplanet.ui.library;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatRatingBar;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnLibraryItemSelected;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.ui.course.AdapterCourses;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.noties.markwon.Markwon;
import io.realm.Realm;

public class AdapterLibrary extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<RealmMyLibrary> libraryList;
    private List<RealmMyLibrary> selectedItems;
    private OnLibraryItemSelected listener;
    private ChipCloudConfig config;
    private OnHomeItemClickListener homeItemClickListener;
    private HashMap<String, JsonObject> ratingMap;
    private OnRatingChangeListener ratingChangeListener;
    private Markwon markwon;

    private Realm realm;

    public AdapterLibrary(Context context, List<RealmMyLibrary> libraryList, HashMap<String, JsonObject> ratingMap, Realm realm) {
        this.ratingMap = ratingMap;
        this.context = context;
        markwon = Markwon.create(context);
        this.realm = realm;
        this.libraryList = libraryList;
        this.selectedItems = new ArrayList<>();
        config = Utilities.getCloudConfig()
                .selectMode(ChipCloud.SelectMode.single);
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }

    }

    public void setRatingChangeListener(OnRatingChangeListener ratingChangeListener) {
        this.ratingChangeListener = ratingChangeListener;
    }

    public List<RealmMyLibrary> getLibraryList() {
        return libraryList;
    }

    public void setLibraryList(List<RealmMyLibrary> libraryList) {
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
            Utilities.log("On bind " + position);
            ((ViewHolderLibrary) holder).title.setText(libraryList.get(position).getTitle());
            Utilities.log(libraryList.get(position).getDescription());
            markwon.setMarkdown(((ViewHolderLibrary) holder).desc, libraryList.get(position).getDescription());
            ((ViewHolderLibrary) holder).timesRated.setText(libraryList.get(position).getTimesRated() + " Total");
            ((ViewHolderLibrary) holder).checkBox.setChecked(selectedItems.contains(libraryList.get(position)));
            ((ViewHolderLibrary) holder).rating.setText(TextUtils.isEmpty(libraryList.get(position).getAverageRating()) ? "0.0" : String.format("%.1f", Double.parseDouble(libraryList.get(position).getAverageRating())));
            ((ViewHolderLibrary) holder).tvDate.setText(TimeUtils.formatDate(libraryList.get(position).getCreatedDate(), "MMM dd, yyyy"));
            displayTagCloud(((ViewHolderLibrary) holder).flexboxDrawable, position);
            holder.itemView.setOnClickListener(view -> openLibrary(libraryList.get(position)));
            ((ViewHolderLibrary) holder).ivDownloaded.setImageResource(libraryList.get(position).isResourceOffline() ? R.drawable.ic_eye : R.drawable.ic_download);
            if (ratingMap.containsKey(libraryList.get(position).getResource_id())) {
                JsonObject object = ratingMap.get(libraryList.get(position).getResource_id());
                AdapterCourses.showRating(object, ((ViewHolderLibrary) holder).rating, ((ViewHolderLibrary) holder).timesRated, ((ViewHolderLibrary) holder).ratingBar);
            } else {
                ((ViewHolderLibrary) holder).ratingBar.setRating(0);
            }
        }
    }

    private void openLibrary(RealmMyLibrary library) {
        if (homeItemClickListener != null) {
            homeItemClickListener.openLibraryDetailFragment(library);
        }
    }


    private void displayTagCloud(FlexboxLayout flexboxDrawable, int position) {
        flexboxDrawable.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(context, flexboxDrawable, config);
        List<RealmTag> tags = realm.where(RealmTag.class).equalTo("db", "resources").equalTo("linkId", libraryList.get(position).getId()).findAll();
        for (RealmTag tag : tags) {
            RealmTag parent = realm.where(RealmTag.class).equalTo("id", tag.getTagId()).findFirst();
            try {
                chipCloud.addChip(parent.getName());
            } catch (Exception err) {
                chipCloud.addChip("--");
            }
            chipCloud.setListener((i, b, b1) -> {
                if (b1 && listener != null) {
                    listener.onTagClicked(parent);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return libraryList.size();
    }

    class ViewHolderLibrary extends RecyclerView.ViewHolder {
        TextView title, desc, rating, tvDate,timesRated, average;
        CheckBox checkBox;
        AppCompatRatingBar ratingBar;
        FlexboxLayout flexboxDrawable;
        LinearLayout llRating;
        ImageView ivDownloaded;

        public ViewHolderLibrary(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            rating = itemView.findViewById(R.id.rating);
            timesRated = itemView.findViewById(R.id.times_rated);
            ratingBar = itemView.findViewById(R.id.rating_bar);
            tvDate = itemView.findViewById(R.id.tv_date);
            checkBox = itemView.findViewById(R.id.checkbox);
            llRating = itemView.findViewById(R.id.ll_rating);
            ivDownloaded = itemView.findViewById(R.id.iv_downloaded);
            average = itemView.findViewById(R.id.average);
            checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
                if (listener != null) {
                    Utilities.handleCheck(b, getAdapterPosition(), (ArrayList) selectedItems, libraryList);
                    listener.onSelectedListChange(selectedItems);
                }
            });
            ratingBar.setOnTouchListener((v1, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP)
                    homeItemClickListener.showRatingDialog("resource", libraryList.get(getAdapterPosition()).getResource_id(), libraryList.get(getAdapterPosition()).getTitle(), ratingChangeListener);
                return true;
            });
            flexboxDrawable = itemView.findViewById(R.id.flexbox_drawable);
        }
    }
}
