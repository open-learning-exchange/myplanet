package org.ole.planet.myplanet.survey;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.userprofile.AdapterMySubmission;

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
                    AdapterMySubmission.openSurvey(listener, examList.get(position).getId(), false);
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
        Button startSurvey, sendSurvey;

        public ViewHolderSurvey(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_title);
            description = itemView.findViewById(R.id.tv_description);
            noSubmission = itemView.findViewById(R.id.tv_no_submissions);
            lastSubDate = itemView.findViewById(R.id.tv_date);
            startSurvey = itemView.findViewById(R.id.start_survey);
            sendSurvey = itemView.findViewById(R.id.send_survey);
            sendSurvey.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    realm_stepExam current = examList.get(getAdapterPosition());
                    if (listener != null)
                        listener.sendSurvey(current);

                }
            });
        }
    }
}
