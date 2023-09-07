package org.ole.planet.myplanet.ui.sync;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.User;
import org.ole.planet.myplanet.ui.team.teamMember.AdapterJoinedMember;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserListAdapter extends BaseAdapter implements Filterable {
    private Context context;
    private List<User> userList;
    private List<User> filteredUserList;

    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(String name, String password);
    }

    public UserListAdapter(Context context, List<User> userList) {
        this.context = context;
        this.userList = userList;
        this.filteredUserList = new ArrayList<>(userList);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @Override
    public int getCount() {
        return filteredUserList.size();
    }

    @Override
    public Object getItem(int position) {
        return filteredUserList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.user_list_item, null);
        }

        TextView userNameTextView = view.findViewById(R.id.userNameTextView);
        CircleImageView userProfile = view.findViewById(R.id.userProfile);

        User user = filteredUserList.get(position);
        userNameTextView.setText(user.getFullName());
        user.getImage();
        if (!user.getImage().isEmpty()) {
            Picasso.get()
                    .load(user.getImage())
                    .placeholder(R.drawable.profile)
                    .error(R.drawable.profile)
                    .into(userProfile);
        }

        view.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(user.getName(), user.getPassword());
            }
        });

        return view;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<User> filteredList = new ArrayList<>();

                if (constraint == null || constraint.length() == 0) {
                    // No filter applied, return the full list
                    filteredList.addAll(userList);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();

                    for (User item : userList) {
                        if (item.getName().toLowerCase().contains(filterPattern) ||
                                item.getPassword().toLowerCase().contains(filterPattern)) {
                            filteredList.add(item);
                        }
                    }
                }

                results.values = filteredList;
                results.count = filteredList.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredUserList.clear();
                filteredUserList.addAll((List<User>) results.values);
                notifyDataSetChanged();
            }
        };
    }
}
