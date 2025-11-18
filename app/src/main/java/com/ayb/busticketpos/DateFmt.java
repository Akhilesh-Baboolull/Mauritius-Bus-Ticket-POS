// DateFmt.java
package com.ayb.busticketpos;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateFmt {
    private static final SimpleDateFormat LOCAL_PREFS_FORMAT = new SimpleDateFormat("dd/MM/yy", Locale.US);
    private static final SimpleDateFormat SERVER_DATE        = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private static final SimpleDateFormat SERVER_DATETIME    = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static String prefsToServerDate(String prefsDate) {
        // prefsDate like "23/08/25"
        try {
            Date d = LOCAL_PREFS_FORMAT.parse(prefsDate);
            return SERVER_DATE.format(d);
        } catch (ParseException e) {
            // fallback to "today"
            return SERVER_DATE.format(new Date());
        }
    }

    public static String nowServerDateTime() {
        return SERVER_DATETIME.format(new Date());
    }

    public static String nowServerTime() {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
    }

}
