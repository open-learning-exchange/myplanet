package org.ole.planet.takeout.survey;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

public class AdapterSurvey extends RecyclerView.Adapter<RecyclerView.ViewHolder> {


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

    }

    @Override
    public int getItemCount() {
        return 0;
    }

    class ViewHolderSurvey extends RecyclerView.ViewHolder{

        public ViewHolderSurvey(View itemView) {
            super(itemView);
        }
    }
}
