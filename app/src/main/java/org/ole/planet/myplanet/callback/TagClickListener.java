package org.ole.planet.myplanet.callback;

import org.ole.planet.myplanet.model.RealmTag;

import java.util.List;

public interface TagClickListener {
    void onTagClicked(RealmTag tag);

    void onOkClicked(List<RealmTag> list);
}
