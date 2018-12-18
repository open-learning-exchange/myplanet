package org.ole.planet.myplanet.utilities;

import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

import java.io.File;

public class FileUtils {
    public static final String SD_PATH = Environment.getExternalStorageDirectory() + "/ole";

    public static boolean externalMemoryAvailable() {
        return android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }

    public static long getAvailableExternalMemorySize() {
        if (externalMemoryAvailable()) {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long availableBlocks = stat.getAvailableBlocksLong();
            return availableBlocks * blockSize;
        } else {
            return 0;
        }
    }

    private static File createFilePath(String folder, String filename) {
        File f = new File(folder);
        if (!f.exists())
            f.mkdirs();
        Utilities.log("Return file " + folder + "/" + filename);
        return new File(f, filename);
    }

    public static File getSDPathFromUrl(String url) {
        return createFilePath(SD_PATH, getFileNameFromUrl(url));
    }

    public static boolean checkFileExist(String url) {
        if (url == null || url.isEmpty())
            return false;
        File f = createFilePath(SD_PATH, getFileNameFromUrl(url));
        return f.exists();

    }

    public static String getFileNameFromUrl(String url) {
        try {
            return url.substring(url.lastIndexOf("/") + 1);
        } catch (Exception e) {
        }
        return "";
    }

    public static String getFileExtension(String address) {

        String filenameArray[] = address.split("\\.");
        String extension = filenameArray[filenameArray.length - 1];
        return extension;
    }

}
