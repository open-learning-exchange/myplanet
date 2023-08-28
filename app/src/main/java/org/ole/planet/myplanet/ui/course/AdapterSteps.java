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
import org.ole.planet.myplanet.ui.userprofile.AdapterOtherInfo;

import java.util.List;

import io.realm.Realm;

public class AdapterSteps extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private RowStepsBinding rowStepsBinding;
    Context context;
    List<RealmCourseStep> list;
    Realm realm;

    public AdapterSteps(Context context, List<RealmCourseStep> list, Realm realm) {
        this.context = context;
        this.list = list;
        this.realm = realm;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowStepsBinding = RowStepsBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new AdapterOtherInfo.ViewHolderOtherInfo(rowStepsBinding.getRoot());
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AdapterOtherInfo.ViewHolderOtherInfo) {
            rowStepsBinding.tvTitle.setText(list.get(position).getStepTitle());
            int size = 0;
            RealmStepExam exam = realm.where(RealmStepExam.class).equalTo("stepId", list.get(position).getId()).findFirst();
            if (exam != null) size = exam.getNoOfQuestions();
            rowStepsBinding.tvDescription.setText(context.getString(R.string.this_test_has) + size + context.getString(R.string.questions));
            holder.itemView.setOnClickListener(view -> rowStepsBinding.tvDescription.setVisibility(rowStepsBinding.tvDescription.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}
