package org.ole.planet.myplanet.callback;

import java.util.List;
import java.util.Map;

public interface OnFilterListener {
    public void filter(String[] subjects, String[] languages, String[] mediums, String[] levels);
    public Map<String, String[]> getData();
    public Map<String, String[]> getSelectedFilter();
}
