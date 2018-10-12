package org.ole.planet.takeout.callback;

import org.ole.planet.takeout.Data.realm_myLibrary;

import java.util.List;

public interface OnLibraryItemSelected {
    void onSelectedListChange(List<realm_myLibrary> list);
    void onTagClicked(String text);
}
