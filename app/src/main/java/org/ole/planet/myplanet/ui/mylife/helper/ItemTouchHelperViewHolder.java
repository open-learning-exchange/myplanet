package org.ole.planet.myplanet.ui.mylife.helper;

import android.support.v7.widget.RecyclerView;

public interface ItemTouchHelperViewHolder {
    void onItemSelected();

    void onItemClear(RecyclerView.ViewHolder viewHolder);
}
