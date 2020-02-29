package org.ole.planet.myplanet.ui.course;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
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
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.noties.markwon.Markwon;
import io.realm.Realm;

public class AdapterCourses extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

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

    public AdapterCourses(Context context, List<RealmMyCourse> courseList, HashMap<String, JsonObject> map) {
        this.map = map;
        this.context = context;
        this.courseList = courseList;
        markwon = Markwon.create(context);
        this.selectedItems = new ArrayList<>();
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }
        config = Utilities.getCloudConfig()
                .selectMode(ChipCloud.SelectMode.single);
    }

    public static void showRating(JsonObject object, TextView average, TextView ratingCount, AppCompatRatingBar ratingBar) {
        average.setText(String.format("%.2f", object.get("averageRating").getAsFloat()));
        ratingCount.setText(object.get("total").getAsInt() + " total");
        if (object.has("ratingByUser"))
            ratingBar.setRating(object.get("ratingByUser").getAsInt());
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_course, parent, false);
        return new ViewHoldercourse(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ViewHoldercourse) {
            ((ViewHoldercourse) holder).title.setText(courseList.get(position).getCourseTitle());
            ((ViewHoldercourse) holder).desc.setText(courseList.get(position).getDescription());
            markwon.setMarkdown(((ViewHoldercourse) holder).desc, courseList.get(position).getDescription());

            ((ViewHoldercourse) holder).grad_level.setText("Grade Level  : " + courseList.get(position).getGradeLevel());
            ((ViewHoldercourse) holder).subject_level.setText("Subject Level : " + courseList.get(position).getSubjectLevel());
            ((ViewHoldercourse) holder).checkBox.setChecked(selectedItems.contains(courseList.get(position)));
            ((ViewHoldercourse) holder).progressBar.setMax(courseList.get(position).getnumberOfSteps());
            displayTagCloud(((ViewHoldercourse) holder).flexboxLayout, position);
            try{
                ((ViewHoldercourse) holder).tvDate.setText(TimeUtils.formatDate(Long.parseLong(courseList.get(position).getCreatedDate().trim()), "MMM dd, yyyy"));
            }catch (Exception e){

            }
            ((ViewHoldercourse) holder).ratingBar.setOnTouchListener((v1, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP)
                    homeItemClickListener.showRatingDialog("course", courseList.get(position).getCourseId(), courseList.get(position).getCourseTitle(), ratingChangeListener);
                return true;
            });

            ((ViewHoldercourse) holder).checkBox.setOnClickListener((view) -> {
                Utilities.handleCheck(((CheckBox) view).isChecked(), position, (ArrayList) selectedItems, courseList);
                if (listener != null) listener.onSelectedListChange(selectedItems);
                notifyDataSetChanged();
            });
            showProgressAndRating(position, holder);
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

    private void showProgressAndRating(int position, RecyclerView.ViewHolder holder) {
        showProgress(position, holder);
        if (map.containsKey(courseList.get(position).getCourseId())) {
            JsonObject object = map.get(courseList.get(position).getCourseId());
            showRating(object, ((ViewHoldercourse) holder).average, ((ViewHoldercourse) holder).ratingCount, ((ViewHoldercourse) holder).ratingBar);
        } else {
            ((ViewHoldercourse) holder).ratingBar.setRating(0);
        }
    }

    private void showProgress(int position, RecyclerView.ViewHolder holder) {
        if (progressMap.containsKey(courseList.get(position).getCourseId())) {
            JsonObject ob = progressMap.get(courseList.get(position).getCourseId());
            ((ViewHoldercourse) holder).progressBar.setMax(JsonUtils.getInt("max", ob));
            ((ViewHoldercourse) holder).progressBar.setProgress(JsonUtils.getInt("current", ob));
            if (JsonUtils.getInt("current", ob) < JsonUtils.getInt("max", ob))
                ((ViewHoldercourse) holder).progressBar.setSecondaryProgress(JsonUtils.getInt("current", ob) + 1);
            ((ViewHoldercourse) holder).progressBar.setVisibility(View.VISIBLE);
        } else {
            ((ViewHoldercourse) holder).progressBar.setVisibility(View.GONE);
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
        TextView title, desc, grad_level, subject_level,tvDate, ratingCount, average;
        CheckBox checkBox;
        AppCompatRatingBar ratingBar;
        SeekBar progressBar;
        LinearLayout llRating;
        FlexboxLayout flexboxLayout;

        public ViewHoldercourse(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            grad_level = itemView.findViewById(R.id.grad_level);
            average = itemView.findViewById(R.id.rating);
            ratingCount = itemView.findViewById(R.id.times_rated);
            flexboxLayout = itemView.findViewById(R.id.flexbox_drawable);
            tvDate = itemView.findViewById(R.id.tv_date);
            ratingBar = itemView.findViewById(R.id.rating_bar);
            subject_level = itemView.findViewById(R.id.subject_level);
            checkBox = itemView.findViewById(R.id.checkbox);
            llRating = itemView.findViewById(R.id.ll_rating);
            progressBar = itemView.findViewById(R.id.course_progress);
            itemView.setOnClickListener(view -> openCourse(courseList.get(getAdapterPosition()), 0));

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                progressBar.setScaleY(0.3f);
            }

            progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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
    }
}
