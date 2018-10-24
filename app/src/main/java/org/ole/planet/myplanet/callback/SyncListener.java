package org.ole.planet.myplanet.callback;

public interface SyncListener {
    void onSyncStarted();
    void onSyncComplete();
    void onSyncFailed(String msg);
}
