package org.ole.planet.myplanet.ui.mylife;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.callback.OnMyLifeItemSelected;
import org.ole.planet.myplanet.model.RealmMyLife;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;
import java.util.List;
import io.realm.Realm;

public class AdapterMyLife extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private List<RealmMyLife> myLifeList;
    private OnMyLifeItemSelected listener;
    private OnHomeItemClickListener homeItemClickListener;
    private Realm mRealm;

    public List<RealmMyLife> getMyLifeList() {
        return myLifeList;
    }

     public AdapterMyLife(Context context, List<RealmMyLife> myLifeList, Realm realm) {
        this.context = context;
        this.mRealm = realm;
        this.myLifeList = myLifeList;
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
            ((ViewHolderMyLife) holder).positionEditText.setText(Integer.toString(myLifeList.get(position).getWeight()));
            ((ViewHolderMyLife) holder).updatePositionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    String weightString = ((ViewHolderMyLife) holder).positionEditText.getText().toString();
                    int weight = Integer.parseInt(weightString.trim());
                    if(weight <= getItemCount() && weight > 0) {
                        swapPosition(weight, myLifeList.get(position).getTitle(), myLifeList.get(position).getUserId());
                        KeyboardUtils.hideSoftKeyboard((Activity) context);
                        notifyDataSetChanged();
                    } else {
                        Utilities.toast(context, "Please enter a value from 1 to " + getItemCount());
                        ((ViewHolderMyLife) holder).positionEditText.setText(Integer.toString(myLifeList.get(position).getWeight()));
                    }
                }
            });
        }
    }

    public void swapPosition(int weight, String title, String userId){
        RealmMyLife.updateWeight(weight, title, mRealm, userId);
        notifyDataSetChanged();
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
        EditText positionEditText;
        Button updatePositionButton;

        public ViewHolderMyLife(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.titleTextView);
            imageView = itemView.findViewById(R.id.itemImageView);
            positionEditText = itemView.findViewById(R.id.positionEditText);
            updatePositionButton = itemView.findViewById(R.id.updatePosition);
        }

    }

}
