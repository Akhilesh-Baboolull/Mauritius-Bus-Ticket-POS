// DateFmt.java
package com.ayb.busticketpos;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * SimpleDateFormat is not thread-safe and these helpers are called from several sync threads,
 * so each call creates its own formatter.
 */
public final class DateFmt {

    private DateFmt() {}

    public static String prefsToServerDate(String prefsDate) {
        // prefsDate like "23/08/25"
        try {
            if (prefsDate != null) {
                Date d = new SimpleDateFormat("dd/MM/yy", Locale.US).parse(prefsDate);
                if (d != null) {
                    return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
                }
            }
        } catch (Exception ignored) {
            // fall through to "today"
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static String nowServerDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    public static String nowServerTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

}
