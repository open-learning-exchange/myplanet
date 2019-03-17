package org.ole.planet.myplanet.ui.submission;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.ui.exam.TakeExamFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;

public class AdapterMySubmission extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private OnHomeItemClickListener listener;
    private Context context;
    private List<RealmSubmission> list;
    private HashMap<String, RealmStepExam> examHashMap;
    private String type = "";

    public AdapterMySubmission(Context context, List<RealmSubmission> list, HashMap<String, RealmStepExam> exams) {
        this.context = context;
        this.list = list;
        this.examHashMap = exams;
        if (context instanceof OnHomeItemClickListener) {
            this.listener = (OnHomeItemClickListener) context;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_mysurvey, parent, false);
        return new ViewHolderMySurvey(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderMySurvey) {
            ((ViewHolderMySurvey) holder).status.setText(list.get(position).getStatus());
            ((ViewHolderMySurvey) holder).date.setText(Utilities.formatDate(list.get(position).getStartTime()));
            ((ViewHolderMySurvey) holder).submitted_by.setVisibility(View.VISIBLE);
            try {
                JSONObject ob = new JSONObject(list.get(position).getUser());
                ((ViewHolderMySurvey) holder).submitted_by.setText(ob.optString("name"));
            } catch (Exception e) {
                ((ViewHolderMySurvey) holder).submitted_by.setText("Unknown");
                e.printStackTrace();
            }
            if (examHashMap.containsKey(list.get(position).getParentId()))
                ((ViewHolderMySurvey) holder).title.setText(examHashMap.get(list.get(position).getParentId()).getName());
            holder.itemView.setOnClickListener(view -> {
                if (type.equals("survey"))
                    openSurvey(listener, list.get(position).getId(), true);
                else
                    openSubmissionDetail(listener, list.get(position).getId());
            });
        }
    }

    private void openSubmissionDetail(OnHomeItemClickListener listener, String id) {
        if (listener != null) {

        }
    }

    public static void openSurvey(OnHomeItemClickListener listener, String id, boolean isMySurvey) {
        if (listener != null) {
            Bundle b = new Bundle();
            b.putString("type", "survey");
            b.putString("id", id);
            b.putBoolean("isMySurvey", isMySurvey);
            Fragment f = new TakeExamFragment();
            f.setArguments(b);
            listener.openCallFragment(f);
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public void setType(String type) {
        this.type = type;
    }

    class ViewHolderMySurvey extends RecyclerView.ViewHolder {
        TextView title, status, date, submitted_by;

        public ViewHolderMySurvey(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            status = itemView.findViewById(R.id.status);
            date = itemView.findViewById(R.id.date);
            submitted_by = itemView.findViewById(R.id.submitted_by);

        }
    }
}
