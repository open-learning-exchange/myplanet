//package org.ole.planet.myplanet.ui.mylife;
//
//import android.content.Context;
//import android.support.annotation.NonNull;
//import android.support.v7.widget.AppCompatRatingBar;
//import android.support.v7.widget.RecyclerView;
//import android.text.TextUtils;
//import android.view.LayoutInflater;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.CheckBox;
//import android.widget.ImageView;
//import android.widget.LinearLayout;
//import android.widget.TextView;
//
//import com.google.android.flexbox.FlexboxLayout;
//import com.google.gson.JsonObject;
//
//import org.ole.planet.myplanet.R;
//import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
//import org.ole.planet.myplanet.callback.OnMyLifeItemSelected;
//import org.ole.planet.myplanet.callback.OnRatingChangeListener;
//import org.ole.planet.myplanet.model.RealmMyLife;
//import org.ole.planet.myplanet.model.RealmTag;
//import org.ole.planet.myplanet.ui.course.AdapterCourses;
//import org.ole.planet.myplanet.utilities.Constants;
//import org.ole.planet.myplanet.utilities.Utilities;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//
//import fisk.chipcloud.ChipCloud;
//import fisk.chipcloud.ChipCloudConfig;
//import io.realm.Realm;
//
//public class AdapterMyLife extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
//
//    private Context context;
//    private List<RealmMyLife> myLifeList;
//    private List<RealmMyLife> selectedItems;
//    private OnMyLifeItemSelected listener;
//    private OnHomeItemClickListener homeItemClickListener;
//    private Realm realm;
//    //  private HashMap<String, RealmTag> tagMap;
//    // private HashMap<String, RealmTag> tagMapWithName;
//
//
//    public List<RealmMyLife> getMyLifeList() {
//        return myLifeList;
//    }
//
//    //    public AdapterMyLife(Context context, List<RealmMyMyLife> myLifeList, HashMap<String, JsonObject> ratingMap, HashMap<String, RealmTag> tagMap) {
//    public AdapterMyLife(Context context, List<RealmMyLife> myLifeList, Realm realm) {
//        this.context = context;
//        this.realm = realm;
//        this.myLifeList = myLifeList;
//        this.selectedItems = new ArrayList<>();
//        if (context instanceof OnHomeItemClickListener) {
//            homeItemClickListener = (OnHomeItemClickListener) context;
//        }
//
//    }
//
//    public void setMyLifeList(List<RealmMyLife> myLifeList) {
//        this.myLifeList = myLifeList;
//        notifyDataSetChanged();
//    }
//
//    public void setListener(OnMyLifeItemSelected listener) {
//        this.listener = listener;
//    }
//
//    @NonNull
//    @Override
//    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        View v = LayoutInflater.from(context).inflate(R.layout.row_myLife, parent, false);
//        return new org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife(v);
//    }
//
//    @Override
//    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
//        if (holder instanceof org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) {
//            Utilities.log("On bind " + position);
//            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).title.setText((position + 1) + ". " + myLifeList.get(position).getTitle());
//            ((org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) holder).imageId.setText(myLifeList.get(position).getImageId());
//            holder.itemView.setOnClickListener(view -> openMyLife(myLifeList.get(position)));
//        }
//    }
//
//    private void openMyLife(RealmMyLife myLife) {
//        if (homeItemClickListener != null) {
////        TODO:    homeItemClickListener.openMyLifeDetailFragment(myLife);
//        }
//    }
//
//
//    @Override
//    public int getItemCount() {
//        return myLifeList.size();
//    }
//
//    class ViewHolderMyLife extends RecyclerView.ViewHolder {
//        TextView title, desc, rating, imageId, average;
//        CheckBox checkBox;
//        AppCompatRatingBar ratingBar;
//        FlexboxLayout flexboxDrawable;
//        LinearLayout llRating;
//        ImageView ivDownloaded;
//
//        public ViewHolderMyLife(View itemView) {
//            super(itemView);
//            title = itemView.findViewById(R.id.title);
//            desc = itemView.findViewById(R.id.description);
//            rating = itemView.findViewById(R.id.rating);
//            imageId = itemView.findViewById(R.id.imageId);
//            ratingBar = itemView.findViewById(R.id.rating_bar);
//            checkBox = itemView.findViewById(R.id.checkbox);
//            llRating = itemView.findViewById(R.id.ll_rating);
//            ivDownloaded = itemView.findViewById(R.id.iv_downloaded);
//            average = itemView.findViewById(R.id.average);
//            checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
//                if (listener != null) {
//                    Utilities.handleCheck(b, getAdapterPosition(), (ArrayList) selectedItems, myLifeList);
//                    listener.onSelectedListChange(selectedItems);
//                }
//            });
//            if (Constants.showBetaFeature(Constants.KEY_RATING, context)) {
//                //  llRating.setOnClickListener(view -> homeItemClickListener.showRatingDialog("resource", myLifeList.get(getAdapterPosition()).getResource_id(), myLifeList.get(getAdapterPosition()).getTitle(), ratingChangeListener));
//            } else {
//                llRating.setOnClickListener(null);
//            }
//            flexboxDrawable = itemView.findViewById(R.id.flexbox_drawable);
//        }
//    }
//}
