package org.ole.planet.myplanet.ui.enterprises;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.ole.planet.myplanet.databinding.RowTeamCalendarBinding;
import org.ole.planet.myplanet.model.RealmMeetup;
import org.ole.planet.myplanet.utilities.TimeUtils;

import java.util.List;

public class AdapterCalendar extends RecyclerView.Adapter<AdapterCalendar.ViewHolderCalendar> {
    private RowTeamCalendarBinding rowTeamCalendarBinding;
    private Context context;
    private List<RealmMeetup> list;

    public AdapterCalendar(Context context, List<RealmMeetup> list) {
        this.context = context;
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolderCalendar onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowTeamCalendarBinding = RowTeamCalendarBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolderCalendar(rowTeamCalendarBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderCalendar holder, int position) {
        RealmMeetup meetup = list.get(position);
        rowTeamCalendarBinding.tvTitle.setText(meetup.getTitle());
        rowTeamCalendarBinding.tvDescription.setText(meetup.getDescription());

        if (meetup.getStartDate() == meetup.getEndDate()) {
            rowTeamCalendarBinding.tvDate.setText(TimeUtils.formatDate(meetup.getStartDate()));
        } else {
            rowTeamCalendarBinding.tvDate.setText(TimeUtils.formatDate(meetup.getStartDate()) + " to " + TimeUtils.formatDate(meetup.getEndDate()));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolderCalendar extends RecyclerView.ViewHolder {
        RowTeamCalendarBinding rowTeamCalendarBinding;

        public ViewHolderCalendar(RowTeamCalendarBinding rowTeamCalendarBinding) {
            super(rowTeamCalendarBinding.getRoot());
            this.rowTeamCalendarBinding = rowTeamCalendarBinding;
        }
    }
}
