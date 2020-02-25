package org.ole.planet.myplanet.base;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayout;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmNews;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.utilities.Constants;

import java.util.HashMap;

import io.realm.Realm;

public abstract class BaseNewsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public Realm mRealm;
    public RealmUserModel currentUser;

    public String getLabel(String s) {
        for (String key : Constants.LABELS.keySet()) {
            if (s.equals(Constants.LABELS.get(key))) {
                return key;
            }
        }
        return "";
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
        RealmNews.createNews(map, mRealm, currentUser);
        notifyDataSetChanged();
    }

    public class ViewHolderNews extends RecyclerView.ViewHolder {
        public TextView tvName, tvDate, tvMessage;
        public ImageView imgEdit, imgDelete, imgUser, newsImage;
        public LinearLayout llEditDelete;
        public FlexboxLayout fbChips;
        public Button btnReply, btnShowReply, btnAddLabel;

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
            newsImage = itemView.findViewById(R.id.img_news);
            fbChips = itemView.findViewById(R.id.fb_chips);
        }
    }
}
