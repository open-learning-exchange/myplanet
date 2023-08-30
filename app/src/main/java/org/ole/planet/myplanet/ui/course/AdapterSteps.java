package org.ole.planet.myplanet.ui.course;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.RowStepsBinding;
import org.ole.planet.myplanet.model.RealmCourseStep;
import org.ole.planet.myplanet.model.RealmStepExam;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

public class AdapterSteps extends RecyclerView.Adapter<AdapterSteps.ViewHolder> {
    private Context context;
    private List<RealmCourseStep> list;
    private Realm realm;
    private List<Boolean> descriptionVisibilityList = new ArrayList<>();
    private int currentlyVisiblePosition = RecyclerView.NO_POSITION;
    public AdapterSteps(Context context, List<RealmCourseStep> list, Realm realm) {
        this.context = context;
        this.list = list;
        this.realm = realm;

        for (int i = 0; i < list.size(); i++) {
            descriptionVisibilityList.add(false);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RowStepsBinding rowStepsBinding = RowStepsBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(rowStepsBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private RowStepsBinding rowStepsBinding;

        public ViewHolder(RowStepsBinding binding) {
            super(binding.getRoot());
            rowStepsBinding = binding;

            itemView.setOnClickListener(view -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    toggleDescriptionVisibility(position);
                }
            });
        }

        public void bind(int position) {
            RealmCourseStep step = list.get(position);
            rowStepsBinding.tvTitle.setText(step.getStepTitle());
            int size = 0;
            RealmStepExam exam = realm.where(RealmStepExam.class).equalTo("stepId", step.getId()).findFirst();
            if (exam != null) {
                size = exam.getNoOfQuestions();
            }
            rowStepsBinding.tvDescription.setText(context.getString(R.string.this_test_has) + size + context.getString(R.string.questions));

            if (descriptionVisibilityList.get(position)) {
                rowStepsBinding.tvDescription.setVisibility(View.VISIBLE);
            } else {
                rowStepsBinding.tvDescription.setVisibility(View.GONE);
            }
        }
    }

    private void toggleDescriptionVisibility(int position) {
        if (position == currentlyVisiblePosition) {
            currentlyVisiblePosition = RecyclerView.NO_POSITION;
        } else {
            currentlyVisiblePosition = position;
        }
        notifyDataSetChanged();
    }
}

