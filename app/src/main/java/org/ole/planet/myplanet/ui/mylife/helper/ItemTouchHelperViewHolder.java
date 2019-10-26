package org.ole.planet.myplanet.ui.mylife.helper;

import androidx.recyclerview.widget.RecyclerView;

public interface ItemTouchHelperViewHolder {
    void onItemSelected();

    void onItemClear(RecyclerView.ViewHolder viewHolder);
}
