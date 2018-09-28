package org.ole.planet.takeout.callback;

public interface SyncListener {
    void onSyncStarted();
    void onSyncComplete();
    void onSyncFailed();
}
