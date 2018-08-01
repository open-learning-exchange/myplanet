package org.ole.planet.takeout.callback;

import org.ole.planet.takeout.Data.realm_resources;

import java.util.List;

public interface OnLibraryItemSelected {
    void onSelectedListChange(List<realm_resources> list);
}
