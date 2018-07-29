package org.ole.planet.takeout.callback;

import org.ole.planet.takeout.Data.realm_courses;

import java.util.List;

public interface OnCourseItemSelected {
    void onSelectedListChange(List<realm_courses> list);
}