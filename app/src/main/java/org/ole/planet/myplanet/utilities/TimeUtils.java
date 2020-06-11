package org.ole.planet.myplanet.utilities;

import java.text.ParseException;
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


    public static int getAge(String date) {
        SimpleDateFormat dateformat1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat dateformat2 = new SimpleDateFormat("yyyy-MM-dd");
        Calendar dob = Calendar.getInstance();
        Calendar today = Calendar.getInstance();
        try {
            if (date.contains("T")) {
                Date dt = dateformat1.parse(date.replaceAll("T", " ").replaceAll(".000Z", ""));
                dob.setTime(dt);

            } else {
                Date dt2 = dateformat2.parse(date);
                dob.setTime(dt2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
        if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
            age--;
        }
        return age;
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

    public static long currentDateLong() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat dateformat = new SimpleDateFormat("EEE dd, MMMM yyyy");
        try {
            c.setTime(dateformat.parse(currentDate()));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return c.getTimeInMillis();
    }

    public static String formatDate(long date) {
        SimpleDateFormat dateformat = new SimpleDateFormat("EEE dd, MMMM yyyy");
        String datetime = dateformat.format(date);
        return datetime;
    }

    public static String formatDate(long date, String format) {
        SimpleDateFormat dateformat = new SimpleDateFormat(format);
        String datetime = dateformat.format(date);
        return datetime;
    }


    public static long dateToLong(String date) {
        try {
            SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return dateformat.parse(date).getTime();
        } catch (Exception e) {

        }
        return 0;
    }


}
