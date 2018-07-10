package org.ole.planet.takeout.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.library.LibraryFragment;

import java.util.List;

public class AdapterResources extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
   private Context context;
   private List<realm_myLibrary> libraryList;


    public AdapterResources(Context context, List<realm_myLibrary> libraryList) {
        this.context = context;
        this.libraryList = libraryList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_list_library, parent, false);
        return new ViewHolderLibraryList(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderLibraryList){
            ((ViewHolderLibraryList) holder).title.setText(libraryList.get(position).getTitle());
            Log.d("Adapter Resources", "onBindViewHolder: remote address " + libraryList.get(position).getResourceRemoteAddress());
        }
    }

    @Override
    public int getItemCount() {
        return libraryList.size();
    }

    class ViewHolderLibraryList extends RecyclerView.ViewHolder{
        TextView title;
        TextView btnDownload;
        public ViewHolderLibraryList(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }


}
