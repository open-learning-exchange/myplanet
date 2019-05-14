package org.ole.planet.myplanet.utilities;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    public static String getFormatedDateWithTime(long date) {
        Date d = new Date(date);
        SimpleDateFormat dateformat = new SimpleDateFormat("EEE dd, MMMM yyyy , hh:mm aa");
        return dateformat.format(d);
    }

    public static String getformatedDate(String stringDate, String pattern) {
        try {
            Date date = new SimpleDateFormat(pattern, Locale.getDefault()).parse(stringDate);
            return getFormatedDate(date.getTime());
        }catch (Exception e) {
            e.printStackTrace();
            return "N/A";
        }
    }

}
