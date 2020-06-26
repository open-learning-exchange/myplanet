package org.ole.planet.myplanet.ui.enterprises;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

class AdapterCalendar extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmMeetup> list;

    public AdapterCalendar(Context context, List<RealmMeetup> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_team_calendar, parent, false);
        return new ViewHolderCalendar(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderCalendar) {
            ((ViewHolderCalendar) holder).title.setText(list.get(position).getTitle());
            ((ViewHolderCalendar) holder).description.setText(list.get(position).getDescription());
            if (list.get(position).getStartDate() == list.get(position).getEndDate()) {
                ((ViewHolderCalendar) holder).date.setText(TimeUtils.formatDate(list.get(position).getStartDate()));
            } else {
                ((ViewHolderCalendar) holder).date.setText(TimeUtils.formatDate(list.get(position).getStartDate()) + " to " + TimeUtils.formatDate(list.get(position).getEndDate()));
            }
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderCalendar extends RecyclerView.ViewHolder {
        TextView title, description, date;

        public ViewHolderCalendar(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_title);
            description = itemView.findViewById(R.id.tv_description);
            date = itemView.findViewById(R.id.tv_date);
        }
    }
}
