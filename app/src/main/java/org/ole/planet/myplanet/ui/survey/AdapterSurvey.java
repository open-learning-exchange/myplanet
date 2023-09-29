package org.ole.planet.myplanet.ui.survey;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.databinding.RowSurveyBinding;
import org.ole.planet.myplanet.model.RealmExamQuestion;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.ui.submission.AdapterMySubmission;

import java.util.List;

import io.realm.Realm;

public class AdapterSurvey extends RecyclerView.Adapter<AdapterSurvey.ViewHolderSurvey> {
    private RowSurveyBinding rowSurveyBinding;
    private Context context;
    private List<RealmStepExam> examList;
    private OnHomeItemClickListener listener;
    private Realm mRealm;
    private String userId;

    public AdapterSurvey(Context context, List<RealmStepExam> examList, Realm mRealm, String userId) {
        this.context = context;
        this.examList = examList;
        this.mRealm = mRealm;
        this.userId = userId;
        if (context instanceof OnHomeItemClickListener) {
            this.listener = (OnHomeItemClickListener) context;
        }
    }

    @NonNull
    @Override
    public ViewHolderSurvey onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowSurveyBinding = RowSurveyBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderSurvey(rowSurveyBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderSurvey holder, final int position) {
        rowSurveyBinding.tvTitle.setText(examList.get(position).getName());
        rowSurveyBinding.startSurvey.setOnClickListener(view -> AdapterMySubmission.openSurvey(listener, examList.get(position).getId(), false));

        List<RealmExamQuestion> questions = mRealm.where(RealmExamQuestion.class).equalTo("examId", examList.get(position).getId()).findAll();
        if (questions.size() == 0) {
            rowSurveyBinding.sendSurvey.setVisibility(View.GONE);
            rowSurveyBinding.startSurvey.setVisibility(View.GONE);
        }
        rowSurveyBinding.startSurvey.setText(examList.get(position).isFromNation() ? context.getString(R.string.take_survey) : context.getString(R.string.record_survey));
        String noOfSubmission = RealmSubmission.getNoOfSubmissionByUser(examList.get(position).getId(), userId, mRealm);
        String subDate = RealmSubmission.getRecentSubmissionDate(examList.get(position).getId(), userId, mRealm);
        rowSurveyBinding.tvNoSubmissions.setText(noOfSubmission);
        rowSurveyBinding.tvDate.setText(subDate);
    }

    @Override
    public int getItemCount() {
        return examList.size();
    }

    class ViewHolderSurvey extends RecyclerView.ViewHolder {
        RowSurveyBinding rowSurveyBinding;

        public ViewHolderSurvey(RowSurveyBinding rowSurveyBinding) {
            super(rowSurveyBinding.getRoot());
            this.rowSurveyBinding = rowSurveyBinding;
            rowSurveyBinding.startSurvey.setVisibility(View.VISIBLE);

            //Don't show sent survey feature
            rowSurveyBinding.sendSurvey.setVisibility(View.GONE);
            rowSurveyBinding.sendSurvey.setOnClickListener(view -> {
                RealmStepExam current = examList.get(getAdapterPosition());
                if (listener != null) listener.sendSurvey(current);
            });
        }
    }
}
