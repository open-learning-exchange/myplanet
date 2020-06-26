package org.ole.planet.myplanet.base;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.Calendar;
import java.util.HashMap;

import io.realm.Realm;

public abstract class BaseNewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public Realm mRealm;
    public Context context;
    public RealmUserModel currentUser;
    public boolean fromLogin;

    public String getLabel(String s) {
        for (String key : Constants.LABELS.keySet()) {
            if (s.equals(Constants.LABELS.get(key))) {
                return key;
            }
        }
        return "";
    }


    public void showShareButton(RecyclerView.ViewHolder holder, RealmNews news) {
        ((ViewHolderNews) holder).btnShare.setVisibility((news.isCommunityNews() || fromLogin) ? View.GONE : View.VISIBLE);
        ((ViewHolderNews) holder).btnShare.setOnClickListener(view -> {
            JsonArray array = new Gson().fromJson(news.getViewIn(), JsonArray.class);
            JsonObject ob = new JsonObject();
            ob.addProperty("section", "community");
            ob.addProperty("_id", currentUser.getPlanetCode() + "@" + currentUser.getParentCode());
            ob.addProperty("sharedDate", Calendar.getInstance().getTimeInMillis());
            array.add(ob);
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            news.setViewIn(new Gson().toJson(array));
            mRealm.commitTransaction();
            Utilities.toast(context, "Shared to community");
            ((ViewHolderNews) holder).btnShare.setVisibility(View.GONE);
        });
    }


    public class ViewHolderNews extends RecyclerView.ViewHolder {
        public TextView tvName, tvDate, tvMessage;
        public ImageView imgEdit, imgDelete, imgUser, newsImage;
        public LinearLayout llEditDelete;
        public FlexboxLayout fbChips;
        public Button btnReply, btnShowReply, btnAddLabel, btnShare;

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
            btnAddLabel = itemView.findViewById(R.id.btn_add_label);
            btnShare = itemView.findViewById(R.id.btn_share);
            newsImage = itemView.findViewById(R.id.img_news);
            fbChips = itemView.findViewById(R.id.fb_chips);
        }
    }


}
