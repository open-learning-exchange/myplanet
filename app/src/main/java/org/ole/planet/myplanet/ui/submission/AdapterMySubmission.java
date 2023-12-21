package org.ole.planet.myplanet.ui.submission;

import static io.realm.internal.SyncObjectServerFacade.getApplicationContext;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.databinding.RowMysurveyBinding;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.ui.exam.TakeExamFragment;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;

import io.realm.Realm;

public class AdapterMySubmission extends RecyclerView.Adapter<AdapterMySubmission.ViewHolderMySurvey> {
    private RowMysurveyBinding rowMysurveyBinding;
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
            Toast.makeText(getApplicationContext(), context.getString(R.string.no_items), Toast.LENGTH_SHORT).show();
        }
    }

    public void setmRealm(Realm mRealm) {
        this.mRealm = mRealm;
    }

    @NonNull
    @Override
    public ViewHolderMySurvey onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowMysurveyBinding = RowMysurveyBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderMySurvey(rowMysurveyBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderMySurvey holder, int position) {
        rowMysurveyBinding.status.setText(list.get(position).getStatus());
        rowMysurveyBinding.date.setText(TimeUtils.getFormatedDate(list.get(position).getStartTime()));
            showSubmittedBy(rowMysurveyBinding.submittedBy, position);
            if (examHashMap.containsKey(list.get(position).getParentId()))
                rowMysurveyBinding.title.setText(examHashMap.get(list.get(position).getParentId()).name);
            holder.itemView.setOnClickListener(view -> {
                if (type.equals("survey")) openSurvey(listener, list.get(position).getId(), true);
                else openSubmissionDetail(listener, list.get(position).getId());
            });
    }

    private void showSubmittedBy(TextView submitted_by, int position) {
        submitted_by.setVisibility(View.VISIBLE);
        try {
            JSONObject ob = new JSONObject(list.get(position).getUser());
            submitted_by.setText(ob.optString("name"));
        } catch (Exception e) {
            RealmUserModel user = mRealm.where(RealmUserModel.class).equalTo("id", list.get(position).getUserId()).findFirst();
            if (user != null) submitted_by.setText(user.name);
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

    static class ViewHolderMySurvey extends RecyclerView.ViewHolder {
        RowMysurveyBinding rowMysurveyBinding;

        public ViewHolderMySurvey(RowMysurveyBinding rowMysurveyBinding) {
            super(rowMysurveyBinding.getRoot());
            this.rowMysurveyBinding = rowMysurveyBinding;
        }
    }
}
