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
import org.ole.planet.myplanet.databinding.AlertMyPersonalBinding;
import org.ole.planet.myplanet.databinding.RowFeedbackBinding;
import org.ole.planet.myplanet.databinding.RowMyPersonalBinding;
import org.ole.planet.myplanet.model.RealmMyPersonal;
import org.ole.planet.myplanet.ui.feedback.AdapterFeedback;
import org.ole.planet.myplanet.ui.viewer.ImageViewerActivity;
import org.ole.planet.myplanet.ui.viewer.PDFReaderActivity;
import org.ole.planet.myplanet.ui.viewer.VideoPlayerActivity;
import org.ole.planet.myplanet.utilities.IntentUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.List;

import io.realm.Realm;

public class AdapterMyPersonal extends RecyclerView.Adapter<AdapterMyPersonal.ViewHolderMyPersonal> {
    private RowMyPersonalBinding rowMyPersonalBinding;
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
    public ViewHolderMyPersonal onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        rowMyPersonalBinding = RowMyPersonalBinding.inflate(LayoutInflater.from(context), parent, false);
        return new ViewHolderMyPersonal(rowMyPersonalBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolderMyPersonal holder, int position) {
        rowMyPersonalBinding.title.setText(list.get(position).title);
        rowMyPersonalBinding.description.setText(list.get(position).description);
        rowMyPersonalBinding.date.setText(TimeUtils.getFormatedDate(list.get(position).date));
        rowMyPersonalBinding.imgDelete.setOnClickListener(view -> new AlertDialog.Builder(context).setMessage(R.string.delete_record).setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            if (!realm.isInTransaction()) realm.beginTransaction();
            RealmMyPersonal personal = realm.where(RealmMyPersonal.class).equalTo("_id", list.get(position).get_id()).findFirst();
            personal.deleteFromRealm();
            realm.commitTransaction();
            notifyDataSetChanged();
            listener.onAddedResource();
        }).setNegativeButton(R.string.cancel, null).show());

        rowMyPersonalBinding.imgEdit.setOnClickListener(view -> {
            editPersonal(list.get(position));
        });
        holder.itemView.setOnClickListener(view -> {
            openResource(list.get(position).path);
        });
        rowMyPersonalBinding.imgUpload.setOnClickListener(v -> {
            if (listener != null) listener.onUpload(list.get(position));
        });
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
        AlertMyPersonalBinding alertMyPersonalBinding = AlertMyPersonalBinding.inflate(LayoutInflater.from(context));
        alertMyPersonalBinding.etDescription.setText(personal.description);
        alertMyPersonalBinding.etTitle.setText(personal.title);

        new AlertDialog.Builder(context)
                .setTitle(R.string.edit_personal)
                .setIcon(R.drawable.ic_edit)
                .setView(alertMyPersonalBinding.getRoot())
                .setPositiveButton(R.string.button_submit, (dialogInterface, i) -> {
                    String title = alertMyPersonalBinding.etDescription.getText().toString().trim();
                    String desc = alertMyPersonalBinding.etTitle.getText().toString().trim();

                    if (title.isEmpty()) {
                        Utilities.toast(context, String.valueOf(R.string.please_enter_title));
                        return;
                    }

                    if (!realm.isInTransaction()) realm.beginTransaction();
                    personal.description = desc;
                    personal.title = title;
                    realm.commitTransaction();

                    notifyDataSetChanged();
                    listener.onAddedResource();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolderMyPersonal extends RecyclerView.ViewHolder {
        RowMyPersonalBinding rowMyPersonalBinding;

        public ViewHolderMyPersonal(RowMyPersonalBinding rowMyPersonalBinding) {
            super(rowMyPersonalBinding.getRoot());
            this.rowMyPersonalBinding = rowMyPersonalBinding;
        }
    }
}
