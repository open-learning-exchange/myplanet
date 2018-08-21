package org.ole.planet.takeout.survey;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_stepExam;
import org.ole.planet.takeout.R;

import java.util.List;

public class AdapterSurvey extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<realm_stepExam> examList;

    public AdapterSurvey(Context context, List<realm_stepExam> examList) {
        this.context = context;
        this.examList = examList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_survey, parent, false);
        return new ViewHolderSurvey(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderSurvey) {
            ViewHolderSurvey ho = (ViewHolderSurvey) holder;
            ho.title.setText(examList.get(position).getName());
        }
    }

    @Override
    public int getItemCount() {
        return examList.size();
    }

    class ViewHolderSurvey extends RecyclerView.ViewHolder {

        TextView title, description, noSubmission, lastSubDate;

        public ViewHolderSurvey(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_title);
            description = itemView.findViewById(R.id.tv_description);
            noSubmission = itemView.findViewById(R.id.tv_no_submissions);
            lastSubDate = itemView.findViewById(R.id.tv_date);

        }
    }
}
