package org.ole.planet.myplanet.callback;

import org.ole.planet.myplanet.model.RealmMyCourse;

import java.util.List;

public interface OnCourseItemSelected {
    void onSelectedListChange(List<RealmMyCourse> list);
}