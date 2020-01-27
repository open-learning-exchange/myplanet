package org.ole.planet.myplanet.ui.submission;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.exam.TakeExamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;

import io.realm.Realm;

import static io.realm.internal.SyncObjectServerFacade.getApplicationContext;

public class AdapterMySubmission extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private OnHomeItemClickListener listener;
    private Context context;
    private List<RealmSubmission> list;
    private HashMap<String, RealmStepExam> examHashMap;
    private String type = "";
    private Realm mRealm;

    public AdapterMySubmission(Context context, List<RealmSubmission> list, HashMap<String, RealmStepExam> exams) {
        this.context = context;
        this.list = list;
        this.examHashMap = exams;
        if (context instanceof OnHomeItemClickListener) {
            this.listener = (OnHomeItemClickListener) context;
        }
        if (list != null && list.isEmpty()) {
            Toast.makeText(getApplicationContext(), "No items", Toast.LENGTH_SHORT).show();
        }
    }

    public void setmRealm(Realm mRealm) {
        this.mRealm = mRealm;
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
            ((ViewHolderMySurvey) holder).date.setText(TimeUtils.getFormatedDate(list.get(position).getStartTime()));
            showSubmittedBy(((ViewHolderMySurvey) holder).submitted_by, position);
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

    private void showSubmittedBy(TextView submitted_by, int position) {
        submitted_by.setVisibility(View.VISIBLE);
        try {
            JSONObject ob = new JSONObject(list.get(position).getUser());
            submitted_by.setText(ob.optString("name"));
        } catch (Exception e) {
                RealmUserModel user = mRealm.where(RealmUserModel.class).equalTo("id", list.get(position).getUserId()).findFirst();
                if (user != null)
                    submitted_by.setText(user.getName());
        }
    }

    private void openSubmissionDetail(OnHomeItemClickListener listener, String id) {
        if (listener != null) {

        }
    }

    public static void openSurvey(OnHomeItemClickListener listener, String id, boolean isMySurvey) {
        Utilities.log("EXAM ID " + id);
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
