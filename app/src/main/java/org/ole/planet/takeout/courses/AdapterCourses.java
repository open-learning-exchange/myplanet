package org.ole.planet.takeout.courses;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_courses;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.OnCourseItemSelected;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

public class AdapterCourses extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<realm_courses> courseList;
    private List<realm_courses> selectedItems;
    private OnCourseItemSelected listener;

    public AdapterCourses(Context context, List<realm_courses> courseList) {
        this.context = context;
        this.courseList = courseList;
        this.selectedItems = new ArrayList<>();
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

        }
    }
//
//    private void handleCheck(boolean b, int i) {
//        if (b) {
//            selectedItems.add(courseList.get(i));
//        } else if (selectedItems.contains(courseList.get(i))) {
//            selectedItems.remove(courseList.get(i));
//        }
//        listener.onSelectedListChange(selectedItems);
//    }


    @Override
    public int getItemCount() {
        return courseList.size();
    }

    class ViewHoldercourse extends RecyclerView.ViewHolder {
        TextView title, desc, grad_level, subject_level;
        CheckBox checkBox;
        ContentLoadingProgressBar progressBar;

        public ViewHoldercourse(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            grad_level = itemView.findViewById(R.id.grad_level);
            subject_level = itemView.findViewById(R.id.subject_level);
            checkBox = itemView.findViewById(R.id.checkbox);
            progressBar = itemView.findViewById(R.id.progress);
        }
    }
}