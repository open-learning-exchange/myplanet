package org.ole.planet.takeout.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.takeout.Data.realm_myLibrary;
import org.ole.planet.takeout.R;
import org.ole.planet.takeout.SyncActivity;
import org.ole.planet.takeout.datamanager.ApiClient;
import org.ole.planet.takeout.datamanager.ApiInterface;
import org.ole.planet.takeout.datamanager.DownloadService;
import org.ole.planet.takeout.library.LibraryDatamanager;
import org.ole.planet.takeout.library.LibraryFragment;
import org.ole.planet.takeout.utils.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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
        if (holder instanceof ViewHolderLibraryList) {
            ((ViewHolderLibraryList) holder).title.setText(libraryList.get(position).getTitle());

            String url = LibraryDatamanager.getAttachmentUrl(libraryList.get(position));
            if (!TextUtils.isEmpty(url)) {
                ((ViewHolderLibraryList) holder).btnDownload.setOnClickListener(view -> {
                    if (!Utilities.checkFileExist(url)) {
                        Utilities.toast(context,"Downloading file please wait...");

                        new DownloadService(context, new DownloadService.DownloadCallback() {
                            @Override
                            public void onSuccess(String s) {
                                Utilities.toast(context,s);
                            }

                            @Override
                            public void onFailure(String e) {
                                Utilities.toast(context,e);
                            }
                        }).downloadFile(url);
                    }



                });
            } else {
                Utilities.toast(context, "url not available");
            }
        }
    }


    @Override
    public int getItemCount() {
        return libraryList.size();
    }

    class ViewHolderLibraryList extends RecyclerView.ViewHolder {
        TextView title;
        ImageView btnDownload;
        CheckBox checkBox;

        public ViewHolderLibraryList(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            btnDownload = itemView.findViewById(R.id.btn_download);
            checkBox = itemView.findViewById(R.id.checkbox);
        }
    }


}
