package org.ole.planet.myplanet.ui.news;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseNewsAdapter;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.JsonUtils;
import org.ole.planet.myplanet.utilities.TimeUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.Sort;

public class AdapterNews extends BaseNewsAdapter {

    private List<RealmNews> list;

    private OnNewsItemClickListener listener;
    private RealmNews parentNews;

    private ChipCloudConfig config;
    private RealmList<String> imageList;

    public void setImageList(RealmList<String> imageList) {
        this.imageList = imageList;
    }

    public AdapterNews(Context context, List<RealmNews> list, RealmUserModel user, RealmNews parentNews) {
        this.context = context;
        this.list = list;
        this.currentUser = user;
        this.parentNews = parentNews;
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.close);
    }

    public void addItem(RealmNews news) {
        list.add(news);
        notifyDataSetChanged();
    }

    public void setFromLogin(boolean fromLogin) {
        this.fromLogin = fromLogin;
    }

    public void setListener(OnNewsItemClickListener listener) {
        this.listener = listener;
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
            if (news.isValid()) {
                RealmUserModel userModel = mRealm.where(RealmUserModel.class).equalTo("id", news.getUserId()).findFirst();
                if (userModel != null && currentUser != null) {
                    ((ViewHolderNews) holder).tvName.setText(userModel.toString());
                    Utilities.loadImage(userModel.getUserImage(), ((ViewHolderNews) holder).imgUser);
                    showHideButtons(userModel, holder);
                } else {
                    ((ViewHolderNews) holder).tvName.setText(news.getUserName());
                    ((ViewHolderNews) holder).llEditDelete.setVisibility(View.GONE);
                }

                showShareButton(holder, news);
                ((ViewHolderNews) holder).tvMessage.setText(news.getMessageWithoutMarkdown());
                ((ViewHolderNews) holder).tvDate.setText(TimeUtils.formatDate(news.getTime()));
                ((ViewHolderNews) holder).imgDelete.setOnClickListener(view -> new AlertDialog.Builder(context).setMessage(R.string.delete_record).setPositiveButton(R.string.ok, (dialogInterface, i) -> deletePost(news)).setNegativeButton(R.string.cancel, null).show());
                ((ViewHolderNews) holder).imgEdit.setOnClickListener(view -> showEditAlert(news.getId(), true));
                ((ViewHolderNews) holder).llEditDelete.setVisibility(fromLogin ? View.GONE : View.VISIBLE);
                ((ViewHolderNews) holder).btnAddLabel.setVisibility(fromLogin ? View.GONE : View.VISIBLE);
                ((ViewHolderNews) holder).btnReply.setVisibility(fromLogin ? View.GONE : View.VISIBLE);
                loadImage(holder, news);
                showReplyButton(holder, news, position);
                holder.itemView.setOnClickListener(v -> context.startActivity(new Intent(context, NewsDetailActivity.class).putExtra("newsId", list.get(position).getId())));
                addLabels(holder, news);
                showChips(holder, news);
            }
        }
    }


    private void addLabels(RecyclerView.ViewHolder holder, RealmNews news) {
        ((ViewHolderNews) holder).btnAddLabel.setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(context, ((ViewHolderNews) holder).btnAddLabel);
            MenuInflater inflater = menu.getMenuInflater();
            inflater.inflate(R.menu.menu_add_label, menu.getMenu());
            menu.setOnMenuItemClickListener(menuItem -> {
                if (!mRealm.isInTransaction())
                    mRealm.beginTransaction();
                news.addLabel(Constants.LABELS.get(menuItem.getTitle() + ""));
                Utilities.toast(context, "Label added.");
                mRealm.commitTransaction();
                showChips(holder, news);
                return false;
            });
            menu.show();
        });
    }

    private void showChips(RecyclerView.ViewHolder holder, RealmNews news) {
        ((ViewHolderNews) holder).fbChips.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(context, ((ViewHolderNews) holder).fbChips, config);

        for (String s : news.getLabels()) {
            chipCloud.addChip(getLabel(s));
            chipCloud.setDeleteListener((i, s1) -> {
                if (!mRealm.isInTransaction())
                    mRealm.beginTransaction();
                news.getLabels().remove(Constants.LABELS.get(s1));
                mRealm.commitTransaction();
                ((ViewHolderNews) holder).btnAddLabel.setEnabled(news.getLabels().size() < 3);
            });
        }
        ((ViewHolderNews) holder).btnAddLabel.setEnabled(news.getLabels().size() < 3);
    }


    private void loadImage(RecyclerView.ViewHolder holder, RealmNews news) {
        if (news.getImageUrls() != null && news.getImageUrls().size() > 0) {
            try {
                JsonObject imgObject = new Gson().fromJson(news.getImageUrls().get(0), JsonObject.class);
                ((ViewHolderNews) holder).newsImage.setVisibility(View.VISIBLE);
                Glide.with(context).load(new File(JsonUtils.getString("imageUrl", imgObject))).into(((ViewHolderNews) holder).newsImage);
            } catch (Exception e) {
            }
        } else {
            loadRemoteImage(holder, news);
        }
    }

    private void loadRemoteImage(RecyclerView.ViewHolder holder, RealmNews news) {
        Utilities.log(news.getImages());
        if (news.getImagesArray().size() > 0) {
            JsonObject ob = news.getImagesArray().get(0).getAsJsonObject();
            String resourceId = JsonUtils.getString("resourceId", ob.getAsJsonObject());
            RealmMyLibrary library = mRealm.where(RealmMyLibrary.class).equalTo("_id", resourceId).findFirst();
            if (library != null) {
                Glide.with(context).load(new File(Utilities.SD_PATH, library.getId() + "/" + library.getResourceLocalAddress())).into(((ViewHolderNews) holder).newsImage);
                ((ViewHolderNews) holder).newsImage.setVisibility(View.VISIBLE);
                return;
            }
        }
        ((ViewHolderNews) holder).newsImage.setVisibility(View.GONE);
    }

    private void showReplyButton(RecyclerView.ViewHolder holder, RealmNews finalNews, int position) {
        if (this.listener == null || this.fromLogin)
            ((ViewHolderNews) holder).btnShowReply.setVisibility(View.GONE);
        ((ViewHolderNews) holder).btnReply.setOnClickListener(view -> showEditAlert(finalNews.getId(), false));
        List<RealmNews> replies = mRealm.where(RealmNews.class).sort("time", Sort.DESCENDING).equalTo("replyTo", finalNews.getId(), Case.INSENSITIVE).findAll();
        ((ViewHolderNews) holder).btnShowReply.setText(String.format(context.getString(R.string.show_replies) + " (%d)", replies.size()));
        ((ViewHolderNews) holder).btnShowReply.setVisibility(replies.size() > 0 ? View.VISIBLE : View.GONE);
        if (position == 0 && parentNews != null)
            ((ViewHolderNews) holder).btnShowReply.setVisibility(View.GONE);

        ((ViewHolderNews) holder).btnShowReply.setOnClickListener(view -> {
            if (listener != null) {
                listener.showReply(finalNews, fromLogin);
            }
        });
    }


    public void showEditAlert(String id, boolean isEdit) {
        View v = LayoutInflater.from(context).inflate(R.layout.alert_input, null);
        EditText et = v.findViewById(R.id.et_input);
        v.findViewById(R.id.ll_image).setVisibility(Constants.showBetaFeature(Constants.KEY_NEWSADDIMAGE, context) ? View.VISIBLE : View.GONE);
        LinearLayout llImage = v.findViewById(R.id.ll_alert_image);
        v.findViewById(R.id.add_news_image).setOnClickListener(view -> listener.addImage(llImage));
        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", id).findFirst();
        if (isEdit)
            et.setText(news.getMessage() + "");
        new AlertDialog.Builder(context).setTitle(isEdit ? R.string.edit_post : R.string.reply).setIcon(R.drawable.ic_edit).setView(v)
                .setPositiveButton(R.string.button_submit, (dialogInterface, i) -> {
                    String s = et.getText().toString();
                    if (isEdit) {
                        editPost(s, news);
                    } else
                        postReply(s, news);
                }).setNegativeButton(R.string.cancel, null).show();
    }


    public void postReply(String s, RealmNews news) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
        HashMap<String, String> map = new HashMap<>();
        map.put("message", s);
        map.put("viewableBy", news.getViewableBy());
        map.put("viewableId", news.getViewableId());
        map.put("replyTo", news.getId());
        map.put("messageType", news.getMessageType());
        map.put("messagePlanetCode", news.getMessagePlanetCode());
        RealmNews.createNews(map, mRealm, currentUser, imageList);
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

    private RealmNews getNews(RecyclerView.ViewHolder holder, int position) {
        RealmNews news;
        if (parentNews != null) {
            if (position == 0) {
                ((CardView) holder.itemView).setCardBackgroundColor(context.getResources().getColor(R.color.md_blue_50));
                news = parentNews;
            } else {
                ((CardView) holder.itemView).setCardBackgroundColor(context.getResources().getColor(R.color.md_white_1000));
                news = list.get(position - 1);
            }
        } else {
            ((CardView) holder.itemView).setCardBackgroundColor(context.getResources().getColor(R.color.md_white_1000));
            news = list.get(position);
        }
        return news;
    }

    private void showHideButtons(RealmUserModel userModel, RecyclerView.ViewHolder holder) {
        if (currentUser.getId().equals(userModel.getId())) {
            ((ViewHolderNews) holder).llEditDelete.setVisibility(View.VISIBLE);
            ((ViewHolderNews) holder).btnAddLabel.setVisibility(View.VISIBLE);
        } else {
            ((ViewHolderNews) holder).llEditDelete.setVisibility(View.GONE);
            ((ViewHolderNews) holder).btnAddLabel.setVisibility(View.GONE);
        }
    }


    private void deletePost(RealmNews news) {
        if (!mRealm.isInTransaction())
            mRealm.beginTransaction();
//        RealmNews news = mRealm.where(RealmNews.class).equalTo("id", id).findFirst();
        list.remove(news);
        if (news != null) {
            news.deleteFromRealm();
        }
        mRealm.commitTransaction();
        notifyDataSetChanged();

    }


    @Override
    public int getItemCount() {
        Utilities.log("Parent news  " + (parentNews == null));
        return parentNews == null ? list.size() : list.size() + 1;
    }


    public interface OnNewsItemClickListener {
        void showReply(RealmNews news, boolean fromLogin);

        void addImage(LinearLayout llImage);
    }


}
