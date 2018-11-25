package org.ole.planet.myplanet.survey;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_stepExam;
import org.ole.planet.myplanet.Data.realm_submissions;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.courses.exam.TakeExamFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;

public class AdapterMySurvey extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private OnHomeItemClickListener listener;
    private Context context;
    private List<realm_submissions> list;
    private HashMap<String, realm_stepExam> examHashMap;

    public AdapterMySurvey(Context context, List<realm_submissions> list, HashMap<String, realm_stepExam> exams) {
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
            ((ViewHolderMySurvey) holder).date.setText(Utilities.formatDate(list.get(position).getDate()));
            if (examHashMap.containsKey(list.get(position).getParentId()))
                ((ViewHolderMySurvey) holder).title.setText(examHashMap.get(list.get(position).getParentId()).getName());
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (listener != null) {
                        Bundle b = new Bundle();
                        b.putString("type", "survey");
                        b.putString("id", list.get(position).getParentId());
                        Fragment f = new TakeExamFragment();
                        f.setArguments(b);
                        listener.openCallFragment(f);
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderMySurvey extends RecyclerView.ViewHolder {
        TextView title, status, date;

        public ViewHolderMySurvey(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            status = itemView.findViewById(R.id.status);
            date = itemView.findViewById(R.id.date);
        }
    }
}
