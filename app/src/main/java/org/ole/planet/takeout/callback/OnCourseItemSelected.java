package org.ole.planet.takeout.callback;

import org.ole.planet.takeout.Data.realm_myCourses;

import java.util.List;

public interface OnCourseItemSelected {
    void onSelectedListChange(List<realm_myCourses> list);
}