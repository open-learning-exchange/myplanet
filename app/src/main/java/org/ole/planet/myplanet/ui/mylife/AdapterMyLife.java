package org.ole.planet.myplanet.ui.mylife;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatRatingBar;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnMyLifeItemSelected;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.ui.course.AdapterCourses;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import io.realm.Realm;

public class AdapterMyLife extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<RealmMyLife> myLifeList;
    private List<RealmMyLife> selectedItems;
    private OnMyLifeItemSelected listener;
    private OnHomeItemClickListener homeItemClickListener;
    private Realm mRealm;
    //  private HashMap<String, RealmTag> tagMap;
    // private HashMap<String, RealmTag> tagMapWithName;


    public List<RealmMyLife> getMyLifeList() {
        return myLifeList;
    }

    //    public AdapterMyLife(Context context, List<RealmMyMyLife> myLifeList, HashMap<String, JsonObject> ratingMap, HashMap<String, RealmTag> tagMap) {
    public AdapterMyLife(Context context, List<RealmMyLife> myLifeList, Realm realm) {
        this.context = context;
        this.mRealm = realm;
        this.myLifeList = myLifeList;
        this.selectedItems = new ArrayList<>();
        if (context instanceof OnHomeItemClickListener) {
            homeItemClickListener = (OnHomeItemClickListener) context;
        }

    }

    public void setMyLifeList(List<RealmMyLife> myLifeList) {
        this.myLifeList = myLifeList;
        notifyDataSetChanged();
    }

    public void setListener(OnMyLifeItemSelected listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_life, parent, false);
        return new org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) {
            Utilities.log("On bind " + position);
            ((ViewHolderMyLife) holder).title.setText(myLifeList.get(position).getTitle());
            ((ViewHolderMyLife) holder).imageView.setImageResource(myLifeList.get(position).getImageId());
//            holder.itemView.setOnClickListener(view -> openMyLife(myLifeList.get(position)));
        }
    }

    private void openMyLife(RealmMyLife myLife) {
        if (homeItemClickListener != null) {
//        TODO:    homeItemClickListener.openMyLifeDetailFragment(myLife);
        }
    }

    public void setmRealm(Realm mRealm) {
        this.mRealm = mRealm;
    }

    @Override
    public int getItemCount() {
        return myLifeList.size();
    }

    class ViewHolderMyLife extends RecyclerView.ViewHolder {
        TextView title;
        ImageView imageView;

        public ViewHolderMyLife(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.titleTextView);
            imageView = itemView.findViewById(R.id.itemImageView);
        }
    }
}
