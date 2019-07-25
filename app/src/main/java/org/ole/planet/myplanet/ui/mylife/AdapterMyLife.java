package org.ole.planet.myplanet.ui.mylife;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatRatingBar;
import android.support.v7.widget.RecyclerView;
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
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.ui.course.AdapterCourses;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.realm.Realm;

public class AdapterMyLife extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<RealmMyLife> myLifeList;
    private List<RealmMyLife> selectedItems;
    private OnLibraryItemSelected listener;
    private ChipCloudConfig config;
    private OnHomeItemClickListener homeItemClickListener;
    private HashMap<String, JsonObject> ratingMap;
    private OnRatingChangeListener ratingChangeListener;
    private Realm realm;
    //  private HashMap<String, RealmTag> tagMap;
    // private HashMap<String, RealmTag> tagMapWithName;

    public void setRatingChangeListener(OnRatingChangeListener ratingChangeListener) {
        this.ratingChangeListener = ratingChangeListener;
    }

    public List<RealmMyLife> getLibraryList() {
        return myLifeList;
    }

    //    public AdapterLibrary(Context context, List<RealmMyLibrary> libraryList, HashMap<String, JsonObject> ratingMap, HashMap<String, RealmTag> tagMap) {
    public AdapterMyLife(Context context, List<RealmMyLife> myLifeList, HashMap<String, JsonObject> ratingMap, Realm realm) {
        this.ratingMap = ratingMap;
        this.context = context;
        this.realm = realm;
        this.myLifeList = myLifeList;
        this.selectedItems = new ArrayList<>();
//        this.tagMap = tagMap;
//        this.tagMapWithName = new HashMap<>();
//        for (String key : tagMap.keySet()
//        ) {
//            RealmTag tag = tagMap.get(key);
//            this.tagMapWithName.put(tag.getName(), tag);
//        }
        config = Utilities.getCloudConfig()
                .selectMode(ChipCloud.SelectMode.single);
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }

    }

    public void setLibraryList(List<RealmMyLife> myLifeList) {
        this.myLifeList = myLifeList;
        notifyDataSetChanged();
    }

    public void setListener(OnLibraryItemSelected listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_library, parent, false);
        return new org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof org.ole.planet.myplanet.ui.mylife.AdapterMyLife..ViewHolderMyLife) {
            Utilities.log("On bind " + position);
            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).title.setText((position + 1) + ". " + libraryList.get(position).getTitle());
            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).desc.setText(myLifeList.get(position).getDescription());
            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).timesRated.setText(myLifeList.get(position).getTimesRated() + " Total");
            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).checkBox.setChecked(selectedItems.contains(myLifeList.get(position)));
            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).rating.setText(TextUtils.isEmpty(myLifeList.get(position).getAverageRating()) ? "0.0" : String.format("%.1f", Double.parseDouble(libraryList.get(position).getAverageRating())));
            displayTagCloud(((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).flexboxDrawable, position);
            holder.itemView.setOnClickListener(view -> openMyLife(myLifeList.get(position)));
            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).ivDownloaded.setImageResource(myLifeList.get(position).isResourceOffline() ? R.drawable.ic_eye : R.drawable.ic_download);
            if (ratingMap.containsKey(myLifeList.get(position).getStringId())) {
                JsonObject object = ratingMap.get(myLifeList.get(position).getStringId());
                AdapterCourses.showRating(object, ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).rating, ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).timesRated, ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).ratingBar);
            } else {
                ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).ratingBar.setRating(0);
            }
        }
    }

    private void openMyLife(RealmMyLife myLife) {
        if (homeItemClickListener != null) {
//        TODO:    homeItemClickListener.openLibraryDetailFragment(myLife);
        }
    }


    private void displayTagCloud(FlexboxLayout flexboxDrawable, int position) {
        flexboxDrawable.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(context, flexboxDrawable, config);
//        for (String s : libraryList.get(position).getTag()) {
//            if (tagMap.containsKey(s)) {
//                chipCloud.addChip(tagMap.get(s));
//                chipCloud.setListener((i, b, b1) -> {
//                    if (b1 && listener != null) {
//                        listener.onTagClicked(tagMapWithName.get(chipCloud.getLabel(i)));
//                    }
//                });
//            }
//        }

        List<RealmTag> tags = realm.where(RealmTag.class).equalTo("db", "resources").equalTo("linkId", myLifeList.get(position).getId()).findAll();
        for (RealmTag tag : tags) {
            RealmTag parent = realm.where(RealmTag.class).equalTo("id", tag.getTagId()).findFirst();
            chipCloud.addChip(parent.getName());
            chipCloud.setListener((i, b, b1) -> {
                if (b1 && listener != null) {
                    listener.onTagClicked(parent);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return myLifeList.size();
    }

    class ViewHolderMyLife extends RecyclerView.ViewHolder {
        TextView title, desc, rating, timesRated, average;
        CheckBox checkBox;
        AppCompatRatingBar ratingBar;
        FlexboxLayout flexboxDrawable;
        LinearLayout llRating;
        ImageView ivDownloaded;

        public ViewHolderMyLife(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            rating = itemView.findViewById(R.id.rating);
            timesRated = itemView.findViewById(R.id.times_rated);
            ratingBar = itemView.findViewById(R.id.rating_bar);
            checkBox = itemView.findViewById(R.id.checkbox);
            llRating = itemView.findViewById(R.id.ll_rating);
            ivDownloaded = itemView.findViewById(R.id.iv_downloaded);
            average = itemView.findViewById(R.id.average);
            checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
                if (listener != null) {
                    Utilities.handleCheck(b, getAdapterPosition(), (ArrayList) selectedItems, myLifeList);
                    listener.onSelectedListChange(selectedItems);
                }
            });
            if (Constants.showBetaFeature(Constants.KEY_RATING, context)) {
                //  llRating.setOnClickListener(view -> homeItemClickListener.showRatingDialog("resource", libraryList.get(getAdapterPosition()).getResource_id(), libraryList.get(getAdapterPosition()).getTitle(), ratingChangeListener));
                ratingBar.setOnTouchListener((v1, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP)
                        homeItemClickListener.showRatingDialog("resource", myLifeList.get(getAdapterPosition()).getStringId(), myLifeList.get(getAdapterPosition()).getImageId(), ratingChangeListener);
                    return true;
                });
            } else {
                llRating.setOnClickListener(null);
            }
            flexboxDrawable = itemView.findViewById(R.id.flexbox_drawable);
        }
    }
}
