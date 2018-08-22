package org.ole.planet.myplanet.library;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnLibraryItemSelected;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

public class AdapterLibrary extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<realm_myLibrary> libraryList;
    private List<realm_myLibrary> selectedItems;
    private OnLibraryItemSelected listener;

    public AdapterLibrary(Context context, List<realm_myLibrary> libraryList) {
        this.context = context;
        this.libraryList = libraryList;
        this.selectedItems = new ArrayList<>();
    }

    public void setListener(OnLibraryItemSelected listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_library, parent, false);
        return new ViewHolderLibrary(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ViewHolderLibrary) {
            ((ViewHolderLibrary) holder).title.setText(libraryList.get(position).getTitle());
            ((ViewHolderLibrary) holder).desc.setText(libraryList.get(position).getDescription());
            ((ViewHolderLibrary) holder).times_rated.setText(libraryList.get(position).getTimesRated() + " Total");
            ((ViewHolderLibrary) holder).checkBox.setChecked(selectedItems.contains(libraryList.get(position)));
            ((ViewHolderLibrary) holder).rating.setText(TextUtils.isEmpty(libraryList.get(position).getAverageRating()) ? "0.0" : String.format("%.1f", Double.parseDouble(libraryList.get(position).getAverageRating())));

        }
    }
//
//    private void handleCheck(boolean b, int i) {
//        if (b) {
//            selectedItems.add(libraryList.get(i));
//        } else if (selectedItems.contains(libraryList.get(i))) {
//            selectedItems.remove(libraryList.get(i));
//        }
//        listener.onSelectedListChange(selectedItems);
//    }


    @Override
    public int getItemCount() {
        return libraryList.size();
    }

    class ViewHolderLibrary extends RecyclerView.ViewHolder {
        TextView title, desc, rating, times_rated;
        CheckBox checkBox;

        public ViewHolderLibrary(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            desc = itemView.findViewById(R.id.description);
            rating = itemView.findViewById(R.id.rating);
            times_rated = itemView.findViewById(R.id.times_rated);
            checkBox = itemView.findViewById(R.id.checkbox);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (listener != null) {
                        Utilities.handleCheck(b, getAdapterPosition(), (ArrayList) selectedItems, libraryList);
                        listener.onSelectedListChange(selectedItems);
                    }
                }
            });
        }
    }
}
