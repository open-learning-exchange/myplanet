package org.ole.planet.myplanet.callback;

import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmTag;

import java.util.List;

public interface OnCourseItemSelected {
    void onSelectedListChange(List<RealmMyCourse> list);
    void onTagClicked(RealmTag tag);
}