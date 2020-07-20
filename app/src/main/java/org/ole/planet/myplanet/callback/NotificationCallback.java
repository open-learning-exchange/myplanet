package org.ole.planet.myplanet.callback;

public interface NotificationCallback {
    void showPendingSurveyDialog();

    void showTaskListDialog();

    void showUserResourceDialog();

    void showResourceDownloadDialog();

    void syncKeyId();

    void forceDownloadNewsImages();

    void downloadDictionary();
}
