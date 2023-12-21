package org.ole.planet.myplanet.ui.library;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnLibraryItemSelected;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.databinding.RowLibraryBinding;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.ui.course.AdapterCourses;
import org.ole.planet.myplanet.utilities.Markdown;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.realm.Realm;

public class AdapterLibrary extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private RowLibraryBinding rowLibraryBinding;
    private Context context;
    private List<RealmMyLibrary> libraryList;
    private List<RealmMyLibrary> selectedItems;
    private OnLibraryItemSelected listener;
    private ChipCloudConfig config;
    private OnHomeItemClickListener homeItemClickListener;
    private HashMap<String, JsonObject> ratingMap;
    private OnRatingChangeListener ratingChangeListener;
    private Realm realm;
    private boolean isAscending = true;
    private boolean isTitleAscending = true;
    private boolean areAllSelected = true;

    public AdapterLibrary(Context context, List<RealmMyLibrary> libraryList, HashMap<String, JsonObject> ratingMap, Realm realm) {
        this.ratingMap = ratingMap;
        this.context = context;
        this.realm = realm;
        this.libraryList = libraryList;
        this.selectedItems = new ArrayList<>();
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single);
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
        rowLibraryBinding = RowLibraryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolderLibrary(rowLibraryBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ViewHolderLibrary) {
            ViewHolderLibrary viewHolder = (ViewHolderLibrary) holder;
            viewHolder.bind();
            viewHolder.rowLibraryBinding.title.setText(libraryList.get(position).title);
            Utilities.log(libraryList.get(position).description);
            Markdown.INSTANCE.setMarkdownText(viewHolder.rowLibraryBinding.description, libraryList.get(position).description);
            viewHolder.rowLibraryBinding.timesRated.setText(libraryList.get(position).timesRated + context.getString(R.string.total));
            viewHolder.rowLibraryBinding.checkbox.setChecked(selectedItems.contains(libraryList.get(position)));
            viewHolder.rowLibraryBinding.rating.setText(TextUtils.isEmpty(libraryList.get(position).averageRating) ? "0.0" : String.format("%.1f", Double.parseDouble(libraryList.get(position).averageRating)));
            viewHolder.rowLibraryBinding.tvDate.setText(TimeUtils.formatDate(Long.parseLong(libraryList.get(position).createdDate.trim()), "MMM dd, yyyy"));

            displayTagCloud(viewHolder.rowLibraryBinding.flexboxDrawable, position);
            holder.itemView.setOnClickListener(view -> openLibrary(libraryList.get(position)));
            viewHolder.rowLibraryBinding.ivDownloaded.setImageResource(libraryList.get(position).isResourceOffline() ? R.drawable.ic_eye : R.drawable.ic_download);
            if (ratingMap.containsKey(libraryList.get(position).resourceId)) {
                JsonObject object = ratingMap.get(libraryList.get(position).resourceId);
                AdapterCourses.showRating(object, viewHolder.rowLibraryBinding.rating, viewHolder.rowLibraryBinding.timesRated, viewHolder.rowLibraryBinding.ratingBar);
            } else {
                viewHolder.rowLibraryBinding.ratingBar.setRating(0);
            }

            viewHolder.rowLibraryBinding.checkbox.setOnClickListener((view) -> {
                Utilities.handleCheck(((CheckBox) view).isChecked(), position, (ArrayList) selectedItems, libraryList);
                if (listener != null) listener.onSelectedListChange(selectedItems);
            });
        }
    }

    public boolean areAllSelected(){
        if (selectedItems.size() != libraryList.size()) {
            areAllSelected = false;
        } else {
            areAllSelected = true;
        }
        return areAllSelected;
    }
    public void selectAllItems(boolean selectAll) {
        if (selectAll) {
            selectedItems.clear();
            selectedItems.addAll(libraryList);
        } else {
            selectedItems.clear();
        }

        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectedListChange(selectedItems);
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
        List<RealmTag> tags = realm.where(RealmTag.class).equalTo("db", "resources").equalTo("linkId", libraryList.get(position).id).findAll();
        for (RealmTag tag : tags) {
            RealmTag parent = realm.where(RealmTag.class).equalTo("id", tag.tagId).findFirst();
            try {
                chipCloud.addChip(parent.name);
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

    public void toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending;
        sortLibraryListByTitle();
        notifyDataSetChanged();
    }

    public void toggleSortOrder() {
        isAscending = !isAscending;
        sortLibraryList();
        notifyDataSetChanged();
    }

    private void sortLibraryListByTitle() {
        Collections.sort(libraryList, (library1, library2) -> {
            if (isTitleAscending) {
                return library1.title.compareToIgnoreCase(library2.title);
            } else {
                return library2.title.compareToIgnoreCase(library1.title);
            }
        });
    }

    private void sortLibraryList() {
        Collections.sort(libraryList, (library1, library2) -> {
            if (isAscending) {
                return library1.createdDate.compareTo(library2.createdDate);
            } else {
                return library2.createdDate.compareTo(library1.createdDate);
            }
        });
    }

    @Override
    public int getItemCount() {
        return libraryList.size();
    }

    class ViewHolderLibrary extends RecyclerView.ViewHolder {
        private final RowLibraryBinding rowLibraryBinding;

        public ViewHolderLibrary(RowLibraryBinding rowLibraryBinding) {
            super(rowLibraryBinding.getRoot());
            this.rowLibraryBinding = rowLibraryBinding;
            rowLibraryBinding.ratingBar.setOnTouchListener((v1, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP)
                    homeItemClickListener.showRatingDialog("resource", libraryList.get(getAdapterPosition()).resourceId, libraryList.get(getAdapterPosition()).title, ratingChangeListener);
                return true;
            });
        }

        public void bind() {
        }
    }
}
