package org.ole.planet.myplanet.ui.news;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.HashMap;
import java.util.List;

import io.realm.Realm;

public class AdapterNews extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmNews> list;
    private Realm mRealm;
    private RealmUserModel currentUser;
    private OnNewsItemClickListener listener;

    interface OnNewsItemClickListener {
        void showReply(RealmNews news);
    }

    public void setListener(OnNewsItemClickListener listener) {
        this.listener = listener;
    }

    public AdapterNews(Context context, List<RealmNews> list, RealmUserModel user) {
        this.context = context;
        this.list = list;
        this.currentUser = user;
    }

    public void setmRealm(Realm mRealm) {
        this.mRealm = mRealm;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_news, parent, false);
        return new ViewHolderNews(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ViewHolderNews) {
            RealmUserModel userModel = mRealm.where(RealmUserModel.class).equalTo("id", list.get(position).getUserId()).findFirst();
            if (userModel != null) {
                ((ViewHolderNews) holder).tvName.setText(userModel.getName());
                Utilities.loadImage(userModel.getUserImage(), ((ViewHolderNews) holder).imgUser);
                showHideButtons(userModel, holder);
            } else {
                ((ViewHolderNews) holder).tvName.setText(list.get(position).getUserName());
                ((ViewHolderNews) holder).llEditDelete.setVisibility(View.GONE);
            }
            ((ViewHolderNews) holder).tvMessage.setText(list.get(position).getMessage());
            ((ViewHolderNews) holder).tvDate.setText(TimeUtils.formatDate(list.get(position).getTime()));
            ((ViewHolderNews) holder).imgDelete.setOnClickListener(view -> new AlertDialog.Builder(context).setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok, (dialogInterface, i) -> deletePost(position)).setNegativeButton(R.string.cancel, null).show());

            ((ViewHolderNews) holder).imgEdit.setOnClickListener(view -> showEditAlert(position, true));
            ((ViewHolderNews) holder).btnReply.setOnClickListener(view -> showEditAlert(position, false));
            List<RealmNews> replies = mRealm.where(RealmNews.class).equalTo("replyTo", list.get(position).getId()).findAll();
            ((ViewHolderNews) holder).btnShowReply.setText("Show replies (" + replies.size() + ")");
            ((ViewHolderNews) holder).btnShowReply.setVisibility(replies.size() > 0 ? View.VISIBLE : View.GONE);
            ((ViewHolderNews) holder).btnShowReply.setOnClickListener(view -> {
                    if (listener!=null){
                        listener.showReply(list.get(position));
                    }
            });

        }
    }

    private void showHideButtons(RealmUserModel userModel, RecyclerView.ViewHolder holder) {
        if (currentUser.getId().equals(userModel.getId())) {
            ((ViewHolderNews) holder).llEditDelete.setVisibility(View.VISIBLE);
        } else {
            ((ViewHolderNews) holder).llEditDelete.setVisibility(View.GONE);
        }
    }

    private void showEditAlert(int position, boolean isEdit) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_input, null);
        EditText et = v.findViewById(R.id.et_input);
        if (isEdit)
        et.setText(list.get(position).getMessage());
        new AlertDialog.Builder(context).setTitle(R.string.edit_post).setIcon(R.drawable.ic_edit)
                .setView(v)
                .setPositiveButton(R.string.button_submit, (dialogInterface, i) -> {
                    String s = et.getText().toString();
                    if (isEdit)
                        editPost(s, position);
                    else
                        postReply(s, position);
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void postReply(String s, int position) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        HashMap<String, String> map = new HashMap<>();
        map.put("message", s);
        map.put("viewableBy", "community");
        map.put("viewableId", "");
        map.put("replyTo", list.get(position).getId());
        RealmNews.createNews(map, mRealm, currentUser);
    }

    private void deletePost(int position) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        list.get(position).deleteFromRealm();
        mRealm.commitTransaction();
        notifyDataSetChanged();
    }

    private void editPost(String s, int position) {
        if (s.isEmpty()) {
            Utilities.toast(context, "Please enter message");
            return;
        }
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        list.get(position).setMessage(s);
        mRealm.commitTransaction();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolderNews extends RecyclerView.ViewHolder {
        TextView tvName, tvDate, tvMessage;
        ImageView imgEdit, imgDelete, imgUser;
        LinearLayout llEditDelete;
        Button btnReply, btnShowReply;

        public ViewHolderNews(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvName = itemView.findViewById(R.id.tv_name);
            tvMessage = itemView.findViewById(R.id.tv_message);
            imgDelete = itemView.findViewById(R.id.img_delete);
            imgEdit = itemView.findViewById(R.id.img_edit);
            imgUser = itemView.findViewById(R.id.img_user);
            llEditDelete = itemView.findViewById(R.id.ll_edit_delete);
            btnReply = itemView.findViewById(R.id.btn_reply);
            btnShowReply = itemView.findViewById(R.id.btn_show_reply);
        }
    }
}
