package org.ole.planet.myplanet.callback;

import org.ole.planet.myplanet.Data.realm_myCourses;

import java.util.List;

public interface OnCourseItemSelected {
    void onSelectedListChange(List<realm_myCourses> list);
}