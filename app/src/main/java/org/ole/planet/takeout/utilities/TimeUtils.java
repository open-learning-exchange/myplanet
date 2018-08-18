package org.ole.planet.takeout.utilities;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtils {
    public static String getFormatedDate(long date) {
        try {
            Date d = new Date(date);
            SimpleDateFormat f = new SimpleDateFormat("EEEE, MMM dd, yyyy");
            return f.format(d);
        } catch (Exception e) {
            Utilities.log("Exception : " + e.getMessage());
            e.printStackTrace();
        }
        return "N/A";
    }
}
