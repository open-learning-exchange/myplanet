package org.ole.planet.myplanet.ui.course;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatRatingBar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnCourseItemSelected;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.databinding.RowCourseBinding;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.noties.markwon.Markwon;
import io.noties.markwon.movement.MovementMethodPlugin;
import io.realm.Realm;

public class AdapterCourses extends RecyclerView.Adapter<AdapterCourses.ViewHoldercourse> {
    private RowCourseBinding rowCourseBinding;
    private Context context;
    private List<RealmMyCourse> courseList;
    private List<RealmMyCourse> selectedItems;
    private OnCourseItemSelected listener;
    private OnHomeItemClickListener homeItemClickListener;
    private HashMap<String, JsonObject> map;
    private HashMap<String, JsonObject> progressMap;
    private OnRatingChangeListener ratingChangeListener;
    private Realm mRealm;
    private ChipCloudConfig config;
    private Markwon markwon;
    private boolean isAscending = true;
    private boolean isTitleAscending = true;
    private boolean areAllSelected = true;

    public AdapterCourses(Context context, List<RealmMyCourse> courseList, HashMap<String, JsonObject> map) {
        this.map = map;
        this.context = context;
        this.courseList = courseList;
        markwon = Markwon.builder(context)
                .usePlugin(MovementMethodPlugin.none())
                .build();
        this.selectedItems = new ArrayList<>();
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.single);
    }

    public static void showRating(JsonObject object, TextView average, TextView ratingCount, AppCompatRatingBar ratingBar) {
        average.setText(String.format("%.2f", object.get("averageRating").getAsFloat()));
        ratingCount.setText(object.get("total").getAsInt() + " total");
        if (object.has("ratingByUser")) ratingBar.setRating(object.get("ratingByUser").getAsInt());
        else ratingBar.setRating(0);
    }

    public void setmRealm(Realm mRealm) {
        this.mRealm = mRealm;
    }

    public void setRatingChangeListener(OnRatingChangeListener ratingChangeListener) {
        this.ratingChangeListener = ratingChangeListener;
    }

    public List<RealmMyCourse> getCourseList() {
        return courseList;
    }

    public void setCourseList(List<RealmMyCourse> courseList) {
        this.courseList = courseList;
        sortCourseList();
        sortCourseListByTitle();
        notifyDataSetChanged();
    }

    private void sortCourseListByTitle() {
        Collections.sort(courseList, (course1, course2) -> {
            if (isTitleAscending) {
                return course1.getCourseTitle().compareToIgnoreCase(course2.getCourseTitle());
            } else {
                return course2.getCourseTitle().compareToIgnoreCase(course1.getCourseTitle());
            }
        });
    }

    private void sortCourseList() {
        Collections.sort(courseList, new Comparator<RealmMyCourse>() {
            @Override
            public int compare(RealmMyCourse course1, RealmMyCourse course2) {
                if (isAscending) {
                    return course1.getCreatedDate().compareTo(course2.getCreatedDate());
                } else {
                    return course2.getCreatedDate().compareTo(course1.getCreatedDate());
                }
            }
        });
    }

    public void toggleTitleSortOrder() {
        isTitleAscending = !isTitleAscending;
        sortCourseListByTitle();
        notifyDataSetChanged();
    }

    public void toggleSortOrder() {
        isAscending = !isAscending;
        sortCourseList();
        notifyDataSetChanged();
    }

    public void setProgressMap(HashMap<String, JsonObject> progressMap) {
        this.progressMap = progressMap;
    }

    public void setListener(OnCourseItemSelected listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHoldercourse onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View v = LayoutInflater.from(context).inflate(R.layout.row_course, parent, false);
//        return new ViewHoldercourse(v);
        rowCourseBinding = RowCourseBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHoldercourse(rowCourseBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHoldercourse holder, final int position) {
        ViewHoldercourse viewHolder = (ViewHoldercourse) holder;
        viewHolder.bind(position);
        rowCourseBinding.title.setText(courseList.get(position).getCourseTitle());
        rowCourseBinding.description.setText(courseList.get(position).getDescription());
        markwon.setMarkdown(rowCourseBinding.description, courseList.get(position).getDescription());

        rowCourseBinding.gradLevel.setText(context.getString(R.string.grade_level_colon) + courseList.get(position).getGradeLevel());
        rowCourseBinding.subjectLevel.setText(context.getString(R.string.subject_level_colon) + courseList.get(position).getSubjectLevel());
        rowCourseBinding.checkbox.setChecked(selectedItems.contains(courseList.get(position)));
        rowCourseBinding.courseProgress.setMax(courseList.get(position).getnumberOfSteps());
        displayTagCloud(rowCourseBinding.flexboxDrawable, position);
        try {
            rowCourseBinding.tvDate.setText(TimeUtils.formatDate(Long.parseLong(courseList.get(position).getCreatedDate().trim()), "MMM dd, yyyy"));
        } catch (Exception e) {

        }
        rowCourseBinding.ratingBar.setOnTouchListener((v1, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP)
                homeItemClickListener.showRatingDialog("course", courseList.get(position).getCourseId(), courseList.get(position).getCourseTitle(), ratingChangeListener);
            return true;
        });

        rowCourseBinding.checkbox.setOnClickListener((view) -> {
            Utilities.handleCheck(((CheckBox) view).isChecked(), position, (ArrayList) selectedItems, courseList);
            if (listener != null) listener.onSelectedListChange(selectedItems);
            notifyDataSetChanged();
        });
        showProgressAndRating(position, holder);
    }

    public boolean areAllSelected(){
        if (selectedItems.size() != courseList.size()) {
            areAllSelected = false;
        }
        return areAllSelected;
    }

    public void selectAllItems(boolean selectAll) {
        if (selectAll) {
            selectedItems.clear();
            selectedItems.addAll(courseList);
        } else {
            selectedItems.clear();
        }

        notifyDataSetChanged();

        if (listener != null) {
            listener.onSelectedListChange(selectedItems);
        }
    }


    private void displayTagCloud(FlexboxLayout flexboxDrawable, int position) {
        flexboxDrawable.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(context, flexboxDrawable, config);
        List<RealmTag> tags = mRealm.where(RealmTag.class).equalTo("db", "courses").equalTo("linkId", courseList.get(position).getId()).findAll();
        showTags(tags, chipCloud);
    }

    private void showTags(List<RealmTag> tags, ChipCloud chipCloud) {
        for (RealmTag tag : tags) {
            RealmTag parent = mRealm.where(RealmTag.class).equalTo("id", tag.getTagId()).findFirst();
            showChip(chipCloud, parent);
        }
    }

    private void showChip(ChipCloud chipCloud, RealmTag parent) {
        chipCloud.addChip(((parent != null) ? parent.getName() : ""));
        chipCloud.setListener((i, b, b1) -> {
            if (b1 && listener != null) {
                listener.onTagClicked(parent);
            }
        });
    }

    private void showProgressAndRating(int position, ViewHoldercourse holder) {
        showProgress(position, holder);
        if (map.containsKey(courseList.get(position).getCourseId())) {
            JsonObject object = map.get(courseList.get(position).getCourseId());
            showRating(object, rowCourseBinding.average, rowCourseBinding.timesRated, rowCourseBinding.ratingBar);
        } else {
            rowCourseBinding.ratingBar.setRating(0);
        }
    }

    private void showProgress(int position, ViewHoldercourse holder) {
        if (progressMap.containsKey(courseList.get(position).getCourseId())) {
            JsonObject ob = progressMap.get(courseList.get(position).getCourseId());
            rowCourseBinding.courseProgress.setMax(JsonUtils.getInt("max", ob));
            rowCourseBinding.courseProgress.setProgress(JsonUtils.getInt("current", ob));
            if (JsonUtils.getInt("current", ob) < JsonUtils.getInt("max", ob))
                rowCourseBinding.courseProgress.setSecondaryProgress(JsonUtils.getInt("current", ob) + 1);
            rowCourseBinding.courseProgress.setVisibility(View.VISIBLE);
        } else {
            rowCourseBinding.courseProgress.setVisibility(View.GONE);
        }
    }

    private void openCourse(RealmMyCourse realm_myCourses, int i) {
        if (homeItemClickListener != null) {
            Fragment f = new TakeCourseFragment();
            Bundle b = new Bundle();
            b.putString("id", realm_myCourses.getCourseId());
            b.putInt("position", i);
            f.setArguments(b);
            homeItemClickListener.openCallFragment(f);
        }
    }

    @Override
    public int getItemCount() {
        return courseList.size();
    }

    class ViewHoldercourse extends RecyclerView.ViewHolder {
//        TextView title, desc, grad_level, subject_level, tvDate, ratingCount, average;
//        CheckBox checkBox;
//        AppCompatRatingBar ratingBar;
//        SeekBar progressBar;
//        LinearLayout llRating;
//        FlexboxLayout flexboxLayout;
        RowCourseBinding rowCourseBinding;

        private int adapterPosition;

        public ViewHoldercourse(RowCourseBinding rowCourseBinding) {
            super(rowCourseBinding.getRoot());
            this.rowCourseBinding = rowCourseBinding;
            itemView.setOnClickListener(v -> {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    openCourse(courseList.get(adapterPosition), 0);
                }
            });

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                rowCourseBinding.courseProgress.setScaleY(0.3f);
            }

            rowCourseBinding.courseProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    if (progressMap.containsKey(courseList.get(getAdapterPosition()).getCourseId())) {
                        JsonObject ob = progressMap.get(courseList.get(getAdapterPosition()).getCourseId());
                        int current = JsonUtils.getInt("current", ob);
                        if (b && i <= current + 1) {
                            openCourse(courseList.get(getAdapterPosition()), seekBar.getProgress());
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        }

        public void bind(int position) {
            adapterPosition = position; // Store the adapter position
        }
    }
}
