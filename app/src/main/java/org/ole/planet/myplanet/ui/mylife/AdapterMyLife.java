package org.ole.planet.myplanet.ui.mylife;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnMyLifeItemSelected;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperAdapter;
import org.ole.planet.myplanet.ui.mylife.helper.ItemTouchHelperViewHolder;
import org.ole.planet.myplanet.ui.mylife.helper.OnStartDragListener;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;

public class AdapterMyLife extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements ItemTouchHelperAdapter {
    private Context context;
    private List<RealmMyLife> myLifeList;
    private Realm mRealm;
    private final OnStartDragListener mDragStartListener;

    public AdapterMyLife(Context context, List<RealmMyLife> myLifeList, Realm realm, OnStartDragListener onStartDragListener)  {
        mDragStartListener = onStartDragListener;
        this.context = context;
        this.mRealm = realm;
        this.myLifeList = myLifeList;
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
            ((ViewHolderMyLife) holder).dragImageButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        mDragStartListener.onStartDrag(holder);
                    }
                    return false;
                }
            });
        }
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
        RealmMyLife.updateWeight(toPosition+1, myLifeList.get(fromPosition).get_id(),mRealm, myLifeList.get(fromPosition).getUserId());
        notifyItemMoved(fromPosition,toPosition);
        return true;
    }

    class ViewHolderMyLife extends RecyclerView.ViewHolder implements ItemTouchHelperViewHolder {
        TextView title;
        ImageView imageView;
        ImageButton editImageButton, dragImageButton;

        public ViewHolderMyLife(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.titleTextView);
            imageView = itemView.findViewById(R.id.itemImageView);
            dragImageButton = itemView.findViewById(R.id.drag_image_button);
            editImageButton = itemView.findViewById(R.id.edit_image_button);
        }

        @Override
        public void onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY);
        }

        @Override
        public void onItemClear() {
            itemView.setBackgroundColor(0);
        }
    }

}

