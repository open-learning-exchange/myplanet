package org.ole.planet.myplanet.callback;

import org.ole.planet.myplanet.Data.realm_courses;

import java.util.List;

public interface OnCourseItemSelected {
    void onSelectedListChange(List<realm_courses> list);
}