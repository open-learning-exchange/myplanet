package org.ole.planet.myplanet.courses;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.AppCompatRatingBar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.Data.realm_myCourses;
import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnCourseItemSelected;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.library.AdapterLibrary;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AdapterCourses extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<realm_myCourses> courseList;
    private List<realm_myCourses> selectedItems;
    private OnCourseItemSelected listener;
    private OnHomeItemClickListener homeItemClickListener;
    private HashMap<String, JsonObject> map;
    private HashMap<String, JsonObject> progressMap;

    public AdapterCourses(Context context, List<realm_myCourses> courseList, HashMap<String, JsonObject> map) {
        this.map = map;
        this.context = context;
        this.courseList = courseList;
        this.selectedItems = new ArrayList<>();
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }
    }

    public void setProgressMap(HashMap<String, JsonObject> progressMap) {
        this.progressMap = progressMap;
    }

    public void setCourseList(List<realm_myCourses> courseList) {
        this.courseList = courseList;
        notifyDataSetChanged();
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
            ((ViewHoldercourse) holder).title.setText((position + 1) + ". " + courseList.get(position).getCourseTitle());
            ((ViewHoldercourse) holder).desc.setText(courseList.get(position).getDescription());
            ((ViewHoldercourse) holder).grad_level.setText("Grad Level  : " + courseList.get(position).getGradeLevel());
            ((ViewHoldercourse) holder).subject_level.setText("Subject Level : " + courseList.get(position).getSubjectLevel());
            ((ViewHoldercourse) holder).checkBox.setChecked(selectedItems.contains(courseList.get(position)));
            if (courseList.get(position) != null) {
                ((ViewHoldercourse) holder).progressBar.setMax(courseList.get(position).getnumberOfSteps());
            }
            ((ViewHoldercourse) holder).checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (listener != null) {
                        Utilities.handleCheck(b, position, (ArrayList) selectedItems, courseList);
                        listener.onSelectedListChange(selectedItems);
                    }
                }
            });
            if (map.containsKey(courseList.get(position).getCourseId())) {
                JsonObject object = map.get(courseList.get(position).getCourseId());
                showRating(object, ((ViewHoldercourse) holder).average, ((ViewHoldercourse) holder).ratingCount, ((ViewHoldercourse) holder).ratingBar);
            }
            showProgress(position, holder);
        }
    }

    private void showProgress(int position, RecyclerView.ViewHolder holder) {
        if (progressMap.containsKey(courseList.get(position).getCourseId())) {
            JsonObject ob = progressMap.get(courseList.get(position).getCourseId());
            ((ViewHoldercourse) holder).progressBar.setMax(JsonUtils.getInt("max", ob));
            ((ViewHoldercourse) holder).progressBar.setProgress(JsonUtils.getInt("current", ob));
            ((ViewHoldercourse) holder).progressBar.setVisibility(View.VISIBLE);
        } else {
            ((ViewHoldercourse) holder).progressBar.setVisibility(View.GONE);
        }
    }


    public static void showRating(JsonObject object, TextView average, TextView ratingCount, AppCompatRatingBar ratingBar) {
        average.setText(object.get("averageRating").getAsFloat() + "");
        ratingCount.setText(object.get("total").getAsInt() + " total");
        ratingBar.setRating(object.get("averageRating").getAsFloat());
    }

    private void openCourse(realm_myCourses realm_myCourses) {
        if (homeItemClickListener != null) {
            Fragment f = new TakeCourseFragment();
            Bundle b = new Bundle();
            b.putString("id", realm_myCourses.getCourseId());
            f.setArguments(b);
            homeItemClickListener.openCallFragment(f);
        }
    }

    @Override
    public int getItemCount() {
        return courseList.size();
    }

    class ViewHoldercourse extends RecyclerView.ViewHolder {
        TextView title, desc, grad_level, subject_level, ratingCount, average;
        CheckBox checkBox;
        AppCompatRatingBar ratingBar;
        SeekBar progressBar;

        public ViewHoldercourse(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            grad_level = itemView.findViewById(R.id.grad_level);
            average = itemView.findViewById(R.id.rating);
            ratingCount = itemView.findViewById(R.id.times_rated);
            ratingBar = itemView.findViewById(R.id.rating_bar);
            subject_level = itemView.findViewById(R.id.subject_level);
            checkBox = itemView.findViewById(R.id.checkbox);
            progressBar = itemView.findViewById(R.id.course_progress);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openCourse(courseList.get(getAdapterPosition()));
                }
            });
        }
    }
}