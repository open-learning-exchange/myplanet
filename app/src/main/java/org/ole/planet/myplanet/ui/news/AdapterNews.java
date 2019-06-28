package org.ole.planet.myplanet.ui.news;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
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

import io.realm.Case;
import io.realm.Realm;
import io.realm.Sort;

public class AdapterNews extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private Context context;
    private List<RealmNews> list;
    private Realm mRealm;
    private RealmUserModel currentUser;
    private OnNewsItemClickListener listener;
    private RealmNews parentNews;


    public interface OnNewsItemClickListener {
        void showReply(RealmNews news);
    }

    public void setListener(OnNewsItemClickListener listener) {
        this.listener = listener;
    }

    public AdapterNews(Context context, List<RealmNews> list, RealmUserModel user, RealmNews parentNews) {
        this.context = context;
        this.list = list;
        this.currentUser = user;
        this.parentNews = parentNews;
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
            RealmNews news = getNews(holder, position);
            RealmUserModel userModel = mRealm.where(RealmUserModel.class).equalTo("id", news.getUserId()).findFirst();
            if (userModel != null) {
                ((ViewHolderNews) holder).tvName.setText(userModel.getName());
                Utilities.loadImage(userModel.getUserImage(), ((ViewHolderNews) holder).imgUser);
                showHideButtons(userModel, holder);
            } else {
                ((ViewHolderNews) holder).tvName.setText(news.getUserName());
                ((ViewHolderNews) holder).llEditDelete.setVisibility(View.GONE);
            }
            ((ViewHolderNews) holder).tvMessage.setText(news.getMessage());
            ((ViewHolderNews) holder).tvDate.setText(TimeUtils.formatDate(news.getTime()));
            ((ViewHolderNews) holder).imgDelete.setOnClickListener(view -> new AlertDialog.Builder(context).setMessage(R.string.delete_record)
                    .setPositiveButton(R.string.ok, (dialogInterface, i) -> deletePost(news.get_id())).setNegativeButton(R.string.cancel, null).show());
            ((ViewHolderNews) holder).imgEdit.setOnClickListener(view -> showEditAlert(news.get_id(), true));
            showReplyButton(holder, news, position);
        }
    }

    private void showReplyButton(RecyclerView.ViewHolder holder, RealmNews finalNews, int position) {
        ((ViewHolderNews) holder).btnReply.setOnClickListener(view -> showEditAlert(finalNews.getId(), false));
        List<RealmNews> replies = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING)
                .equalTo("replyTo", finalNews.getId(), Case.INSENSITIVE)
                .findAll();
        ((ViewHolderNews) holder).btnShowReply.setText(String.format("Show replies (%d)", replies.size()));
        ((ViewHolderNews) holder).btnShowReply.setVisibility(replies.size() > 0 ? View.VISIBLE : View.GONE);
        if (position == 0 && parentNews != null)
            ((ViewHolderNews) holder).btnShowReply.setVisibility(View.GONE);
        ((ViewHolderNews) holder).btnShowReply.setOnClickListener(view -> {
            if (listener != null) {
                listener.showReply(finalNews);
            }
        });
    }

    private RealmNews getNews(RecyclerView.ViewHolder holder, int position) {
        RealmNews news;
        if (parentNews != null) {
            if (position == 0) {
                ((CardView) holder.itemView).setCardBackgroundColor(context.getResources().getColor(R.color.md_blue_50));
                news = mRealm.copyFromRealm(parentNews);
            } else {
                ((CardView) holder.itemView).setCardBackgroundColor(context.getResources().getColor(R.color.md_white_1000));
                news = mRealm.copyFromRealm(list.get(position - 1));
            }
        } else {
            ((CardView) holder.itemView).setCardBackgroundColor(context.getResources().getColor(R.color.md_white_1000));
            news = mRealm.copyFromRealm(list.get(position));
        }
        return news;
    }

    private void showHideButtons(RealmUserModel userModel, RecyclerView.ViewHolder holder) {
        if (currentUser.getId().equals(userModel.getId())) {
            ((ViewHolderNews) holder).llEditDelete.setVisibility(View.VISIBLE);
        } else {
            ((ViewHolderNews) holder).llEditDelete.setVisibility(View.GONE);
        }
    }

    private void showEditAlert(String id, boolean isEdit) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_input, null);
        EditText et = v.findViewById(R.id.et_input);
        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", id).findFirst();
        if (isEdit)
            et.setText(news.getMessage());
        new AlertDialog.Builder(context).setTitle(isEdit ? R.string.edit_post : R.string.reply).setIcon(R.drawable.ic_edit)
                .setView(v)
                .setPositiveButton(R.string.button_submit, (dialogInterface, i) -> {
                    String s = et.getText().toString();
                    if (isEdit)
                        editPost(s, news);
                    else
                        postReply(s, news);
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void postReply(String s, RealmNews news) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        HashMap<String, String> map = new HashMap<>();
        map.put("message", s);
        map.put("viewableBy", "community");
        map.put("viewableId", "");
        map.put("replyTo", news.getId());
        RealmNews.createNews(map, mRealm, currentUser);
        notifyDataSetChanged();
    }

    private void deletePost(String id) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", id).findFirst();
        if (news != null)
            news.deleteFromRealm();
        mRealm.commitTransaction();
        notifyDataSetChanged();
    }

    private void editPost(String s, RealmNews news) {
        if (s.isEmpty()) {
            Utilities.toast(context, "Please enter message");
            return;
        }
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        news.setMessage(s);
        mRealm.commitTransaction();
        notifyDataSetChanged();
    }


    @Override
    public int getItemCount() {
        Utilities.log("Parent news  " + (parentNews == null));
        return parentNews == null ? list.size() : list.size() + 1;
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
