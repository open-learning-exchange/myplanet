package org.ole.planet.myplanet.callback;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface OnFilterListener {
    public void filter(Set<String> subjects, Set<String> languages, Set<String> mediums, Set<String> levels);

    public Map<String, Set<String>> getData();

    public Map<String, Set<String>> getSelectedFilter();
}
