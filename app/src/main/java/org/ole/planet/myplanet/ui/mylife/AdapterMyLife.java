package org.ole.planet.myplanet.ui.mylife;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.calendar.CalendarFragment;
import org.ole.planet.myplanet.ui.helpwanted.HelpWantedFragment;
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment;
import org.ole.planet.myplanet.ui.myhealth.MyHealthFragment;
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperAdapter;
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperViewHolder;
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener;
import org.ole.planet.myplanet.ui.news.NewsFragment;
import org.ole.planet.myplanet.ui.references.ReferenceFragment;
import org.ole.planet.myplanet.ui.submission.MySubmissionFragment;
import org.ole.planet.myplanet.ui.userprofile.AchievementFragment;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.List;

import io.realm.Realm;

public class AdapterMyLife extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ItemTouchHelperAdapter {
    private final OnStartDragListener mDragStartListener;
    private final float HIDE = 0.5f;
    private final float SHOW = 1f;
    private Context context;
    private List<RealmMyLife> myLifeList;
    private Realm mRealm;
    private RealmUserModel user;

    public AdapterMyLife(Context context, List<RealmMyLife> myLifeList, Realm realm, OnStartDragListener onStartDragListener) {
        mDragStartListener = onStartDragListener;
        this.context = context;
        this.mRealm = realm;
        this.myLifeList = myLifeList;
        user = new UserProfileDbHandler(context).getUserModel();
    }

    public static Fragment find_fragment(String frag) {
        switch (frag){
            case  "ic_mypersonals":
                return new MyPersonalsFragment();
            case  "ic_news":
                return new NewsFragment();
            case  "ic_submissions":
                return  new MySubmissionFragment();
            case  "ic_my_survey":
                Utilities.log("My survey");
                return  MySubmissionFragment.newInstance("survey");
            case  "ic_myhealth":
                return new MyHealthFragment();
            case  "ic_calendar":
                return  new CalendarFragment();
            case  "ic_help_wanted":
                return new HelpWantedFragment();
            case  "ic_references":
                return  new ReferenceFragment();
            case  "my_achievement":
                return new AchievementFragment();
        }
        return null;
    }

    public static void transactionFragment(Fragment f, View view) {
        AppCompatActivity activity = (AppCompatActivity) view.getContext();
        activity.getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, f).addToBackStack(null).commit();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.row_life, parent, false);
        return new ViewHolderMyLife(v);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof org.ole.planet.myplanet.ui.mylife.AdapterMyLife.ViewHolderMyLife) {
            Utilities.log("On bind " + position);
            ((ViewHolderMyLife) holder).title.setText(myLifeList.get(position).getTitle());
            ((ViewHolderMyLife) holder).imageView.setImageResource(context.getResources().getIdentifier(myLifeList.get(position).getImageId(), "drawable", context.getPackageName()));
            Fragment fragment = find_fragment(myLifeList.get(position).getImageId());
            if (fragment != null) {
                ((ViewHolderMyLife) holder).imageView.setOnClickListener(view -> transactionFragment(fragment, view));
            }
            ((ViewHolderMyLife) holder).dragImageButton.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                    mDragStartListener.onStartDrag(holder);
                return false;
            });
            ((ViewHolderMyLife) holder).visibility.setOnClickListener(view -> updateVisibility(holder, holder.getAdapterPosition(), myLifeList.get(holder.getAdapterPosition()).isVisible()));
            if (!myLifeList.get(position).isVisible())
                changeVisibility(holder, R.drawable.ic_visibility, HIDE);
            else changeVisibility(holder, R.drawable.ic_visibility_off, SHOW);

        }


    }

    public void updateVisibility(RecyclerView.ViewHolder holder, int position, boolean isVisible) {
        RealmMyLife.updateVisibility(!isVisible, myLifeList.get(position).get_id(), mRealm, myLifeList.get(position).getUserId());
        if (isVisible) {
            changeVisibility(holder, R.drawable.ic_visibility, HIDE);
            Utilities.toast(context, myLifeList.get(position).getTitle() + " is now hidden");
        } else {
            changeVisibility(holder, R.drawable.ic_visibility_off, SHOW);
            Utilities.toast(context, myLifeList.get(position).getTitle() + " is now shown");
        }
    }

    public void changeVisibility(RecyclerView.ViewHolder holder, int imageId, float alpha) {
        ((ViewHolderMyLife) holder).visibility.setImageResource(imageId);
        ((ViewHolderMyLife) holder).rv_item_container.setAlpha(alpha);
    }

    public void setmRealm(Realm mRealm) {
        this.mRealm = mRealm;
    }

    @Override
    public int getItemCount() {
        return myLifeList.size();
    }

    @Override
    public boolean onItemMove(int fromPosition, int toPosition) {
        RealmMyLife.updateWeight(toPosition + 1, myLifeList.get(fromPosition).get_id(), mRealm, myLifeList.get(fromPosition).getUserId());
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }


    class ViewHolderMyLife extends RecyclerView.ViewHolder implements ItemTouchHelperViewHolder {
        TextView title;
        ImageView imageView;
        ImageButton editImageButton, dragImageButton, visibility;
        LinearLayout rv_item_container;

        public ViewHolderMyLife(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.titleTextView);
            imageView = itemView.findViewById(R.id.itemImageView);
            dragImageButton = itemView.findViewById(R.id.drag_image_button);
            editImageButton = itemView.findViewById(R.id.edit_image_button);
            visibility = itemView.findViewById(R.id.visibility_image_button);
            rv_item_container = itemView.findViewById(R.id.rv_item_parent_layout);
        }


        @Override
        public void onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY);
        }

        @Override
        public void onItemClear(RecyclerView.ViewHolder holder) {
            itemView.setBackgroundColor(0);
            if (!myLifeList.get(holder.getAdapterPosition()).isVisible())
                ((ViewHolderMyLife) holder).rv_item_container.setAlpha(HIDE);
        }
    }

}

