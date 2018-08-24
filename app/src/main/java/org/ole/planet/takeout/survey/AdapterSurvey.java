package org.ole.planet.takeout.survey;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.Data.realm_submissions;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.callback.OnHomeItemClickListener;
import org.ole.planet.takeout.courses.exam.TakeExamFragment;

import java.util.List;

import io.realm.Realm;

public class AdapterSurvey extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<realm_stepExam> examList;
    private OnHomeItemClickListener listener;
    private Realm mRealm;
    private String userId;

    public AdapterSurvey(Context context, List<realm_stepExam> examList, Realm mRealm, String userId) {
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
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_survey, parent, false);
        return new ViewHolderSurvey(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ViewHolderSurvey) {
            ViewHolderSurvey ho = (ViewHolderSurvey) holder;
            ho.title.setText(examList.get(position).getName());
            ho.startSurvey.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        Bundle b = new Bundle();
                        b.putString("type", "survey");
                        b.putString("id", examList.get(position).getId());
                        Fragment f = new TakeExamFragment();
                        f.setArguments(b);
                        listener.openCallFragment(f);
                    }
                }
            });
            String noOfSubmission = realm_submissions.getNoOfSubmissionByUser(examList.get(position).getId(), userId, mRealm);
            String subDate = realm_submissions.getRecentSubmissionDate(examList.get(position).getId(), userId, mRealm);
            ho.noSubmission.setText(noOfSubmission);
            ho.lastSubDate.setText(subDate);
        }
    }

    @Override
    public int getItemCount() {
        return examList.size();
    }

    class ViewHolderSurvey extends RecyclerView.ViewHolder {

        TextView title, description, noSubmission, lastSubDate;
        Button startSurvey;

        public ViewHolderSurvey(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_title);
            description = itemView.findViewById(R.id.tv_description);
            noSubmission = itemView.findViewById(R.id.tv_no_submissions);
            lastSubDate = itemView.findViewById(R.id.tv_date);
            startSurvey = itemView.findViewById(R.id.start_survey);
        }
    }
}
