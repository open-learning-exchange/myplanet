package org.ole.planet.takeout.Data;

import android.os.Parcel;
import android.os.Parcelable;

public class Download implements Parcelable {

    public static final Parcelable.Creator<Download> CREATOR = new Parcelable.Creator<Download>() {
        public Download createFromParcel(Parcel in) {
            return new Download(in);
        }

        public Download[] newArray(int size) {
            return new Download[size];
        }
    };
    private String fileName;
    private int progress;
    private int currentFileSize;
    private int totalFileSize;
    private boolean completeAll;
    private boolean failed;

    public Download() {

    }

    private Download(Parcel in) {
        fileName = in.readString();
        progress = in.readInt();
        currentFileSize = in.readInt();
        totalFileSize = in.readInt();
        completeAll = in.readByte() != 0;
        failed = in.readByte() != 0;
    }

    public boolean isCompleteAll() {
        return completeAll;
    }

    public void setCompleteAll(boolean completeAll) {
        this.completeAll = completeAll;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getCurrentFileSize() {
        return currentFileSize;
    }

    public void setCurrentFileSize(int currentFileSize) {
        this.currentFileSize = currentFileSize;
    }

    public int getTotalFileSize() {
        return totalFileSize;
    }

    public void setTotalFileSize(int totalFileSize) {
        this.totalFileSize = totalFileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(fileName);
        dest.writeInt(progress);
        dest.writeInt(currentFileSize);
        dest.writeInt(totalFileSize);
        dest.writeByte((byte) (completeAll ? 1 : 0));
        dest.writeByte((byte) (failed ? 1 : 0));
    }
}