// src/main/java/com/ayb/busticketpos/AssetUtil.java
package com.ayb.busticketpos;

import android.content.Context;
import java.io.IOException;
import java.io.InputStream;

public class AssetUtil {
    /** Reads a file from assets/ into a UTF-8 String */
    public static String loadJSON(Context ctx, String filename) {
        try (InputStream is = ctx.getAssets().open(filename)) {
            byte[] buf = new byte[is.available()];
            is.read(buf);
            return new String(buf, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(
                    "Error loading asset " + filename, e);
        }
    }
}
