package org.ole.planet.myplanet.ui.myPersonals;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnSelectedMyPersonal;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.ui.viewer.ImageViewerActivity;
import org.ole.planet.myplanet.ui.viewer.PDFReaderActivity;
import org.ole.planet.myplanet.ui.viewer.VideoPlayerActivity;
import org.ole.planet.myplanet.utilities.IntentUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.List;

import io.realm.Realm;

public class AdapterMyPersonal extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private Realm realm;
    private List<RealmMyPersonal> list;
    private OnSelectedMyPersonal listener;
    public AdapterMyPersonal(Context context, List<RealmMyPersonal> list) {
        this.context = context;
        this.list = list;
    }

    public void setListener(OnSelectedMyPersonal listener) {
        this.listener = listener;
    }

    public void setRealm(Realm realm) {
        this.realm = realm;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_my_personal, parent, false);
        return new ViewHolderMyPersonal(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderMyPersonal) {
            ((ViewHolderMyPersonal) holder).title.setText(list.get(position).getTitle());
            ((ViewHolderMyPersonal) holder).description.setText(list.get(position).getDescription());
            ((ViewHolderMyPersonal) holder).date.setText(TimeUtils.getFormatedDate(list.get(position).getDate()));
            ((ViewHolderMyPersonal) holder).ivDelete.setOnClickListener(view -> new AlertDialog.Builder(context).setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                        if (!realm.isInTransaction())
                            realm.beginTransaction();
                        RealmMyPersonal personal = realm.where(RealmMyPersonal.class).equalTo("_id", list.get(position).get_id()).findFirst();
                        personal.deleteFromRealm();
                        realm.commitTransaction();
                        notifyDataSetChanged();
                        listener.onAddedResource();
                    }).setNegativeButton(R.string.cancel, null).show());

            ((ViewHolderMyPersonal) holder).ivEdit.setOnClickListener(view -> {
                editPersonal(list.get(position));
            });
            holder.itemView.setOnClickListener(view -> {
                openResource(list.get(position).getPath());
            });
            ((ViewHolderMyPersonal) holder).ivUpload.setOnClickListener(v->{
                if (listener!=null)
                    listener.onUpload(list.get(position));
            });
        }
    }

    private void openResource(String path) {
        String[] arr = path.split("\\.");
        String extension = arr[arr.length - 1];
        switch (extension) {
            case "pdf":
                context.startActivity(new Intent(context, PDFReaderActivity.class).putExtra("TOUCHED_FILE", path));
                break;
            case "bmp":
            case "gif":
            case "jpg":
            case "png":
            case "webp":
                Intent ii = new Intent(context, ImageViewerActivity.class).putExtra("TOUCHED_FILE", path);
                ii.putExtra("isFullPath", true);
                context.startActivity(ii);
                break;
            case "aac":
            case "mp3":
                IntentUtils.openAudioFile(context, path);
                break;
            case "mp4":
               openVideo(path);
                break;
        }
    }

    private void openVideo(String path) {
        Bundle b = new Bundle();
        b.putString("videoURL", "" + Uri.fromFile(new File(path)));
        b.putString("Auth", "" + Uri.fromFile(new File(path)));
        b.putString("videoType", "offline");
        Intent i = new Intent(context, VideoPlayerActivity.class).putExtra("TOUCHED_FILE", path);
        i.putExtras(b);
        context.startActivity(i);
    }


    private void editPersonal(RealmMyPersonal personal) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_my_personal, null);
        EditText etTitle = v.findViewById(R.id.et_title);
        EditText etDesc = v.findViewById(R.id.et_description);
        etDesc.setText(personal.getDescription());
        etTitle.setText(personal.getTitle());
        new AlertDialog.Builder(context).setTitle("Edit Personal").setIcon(R.drawable.ic_edit)
                .setView(v)
                .setPositiveButton(R.string.button_submit, (dialogInterface, i) -> {
                    String title = etTitle.getText().toString().trim();
                    String desc = etDesc.getText().toString().trim();
                    if (title.isEmpty()) {
                        Utilities.toast(context, "Please enter title");
                        return;
                    }
                    if (!realm.isInTransaction())
                        realm.beginTransaction();
                    personal.setDescription(desc);
                    personal.setTitle(title);
                    realm.commitTransaction();
                    notifyDataSetChanged();
                    listener.onAddedResource();
                }).setNegativeButton(R.string.cancel, null).show();


    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderMyPersonal extends RecyclerView.ViewHolder {
        TextView title, description, date;
        ImageView ivEdit, ivDelete,ivUpload;

        public ViewHolderMyPersonal(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            description = itemView.findViewById(R.id.description);
            date = itemView.findViewById(R.id.date);
            ivDelete = itemView.findViewById(R.id.img_delete);
            ivEdit = itemView.findViewById(R.id.img_edit);
            ivUpload = itemView.findViewById(R.id.img_upload);
        }
    }
}
