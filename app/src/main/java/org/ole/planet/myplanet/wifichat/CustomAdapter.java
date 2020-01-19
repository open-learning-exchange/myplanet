package org.ole.planet.myplanet.wifichat;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.ole.planet.myplanet.R;

import java.util.ArrayList;

/**
 * Created by rajeev on 13/3/17.
 */

public class CustomAdapter extends ArrayAdapter<Message> {

    public CustomAdapter(Context context, ArrayList<Message> messages) {
        super(context, R.layout.custom_row, messages);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        View customView = layoutInflater.inflate(R.layout.custom_row, parent, false);

        Message msg = getItem(position);

        TextView message_text= (TextView) customView.findViewById(R.id.message_text);
        TextView time = (TextView) customView.findViewById(R.id.message_time);
        message_text.setText(msg.getMessage());
        time.setText(msg.getTime());
        LinearLayout linearLayout = (LinearLayout) customView.findViewById(R.id.row);
        LinearLayout message_area = (LinearLayout) customView.findViewById(R.id.message_area);

        if(msg.sender) {
            message_area.setBackgroundResource(R.drawable.rounded_corner_2);
            linearLayout.setGravity(Gravity.RIGHT);
        }
        else {
            message_area.setBackgroundResource(R.drawable.rounded_corners);
        }

        int p = (int) (10 * getContext().getResources().getDisplayMetrics().density);
        int p2 = (int) (5 * getContext().getResources().getDisplayMetrics().density);

        message_text.setPadding(p, p2, p, 0);
        time.setPadding(p, 0, p, p2);
        return customView;
    }
}