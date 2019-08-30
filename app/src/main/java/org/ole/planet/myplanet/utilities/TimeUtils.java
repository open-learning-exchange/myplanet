package org.ole.planet.myplanet.utilities;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {


    public static String getFormatedDate(long date) {
        try {
            Date d = new Date(date);
            SimpleDateFormat f = new SimpleDateFormat("EEEE, MMM dd, yyyy");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
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

    public static String formatDateTZ(long data) {
        SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateformat.format(data);
    }

    public static String getFormatedDate(String stringDate, String pattern) {
        try {
            SimpleDateFormat sf = new SimpleDateFormat(pattern, Locale.getDefault());
            sf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sf.parse(stringDate);
            return getFormatedDate(date.getTime());
        } catch (Exception e) {
            e.printStackTrace();
            return "N/A";
        }
    }


    public static String currentDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dateformat = new SimpleDateFormat("EEE dd, MMMM yyyy");
        String datetime = dateformat.format(c.getTime());
        return datetime;
    }

    public static String formatDate(long date) {
        SimpleDateFormat dateformat = new SimpleDateFormat("EEE dd, MMMM yyyy");
        String datetime = dateformat.format(date);
        return datetime;
    }


}
