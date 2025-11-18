// LogSink.java
package com.ayb.busticketpos;

import android.content.Context;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class LogSink {
    private LogSink() {}

    public static synchronized void logTerminalFailure(Context ctx,
                                                       String endpoint,
                                                       String payloadJson,
                                                       String errorMsg) {
        String file = "sync_fail_log.csv";
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date());
        String line = String.format("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                ts,
                endpoint.replace("\"","'"),
                payloadJson.replace("\"","'"),
                (errorMsg == null ? "" : errorMsg.replace("\"","'")));
        try (FileOutputStream fos = ctx.openFileOutput(file, Context.MODE_APPEND)) {
            // write header once if empty
            if (fos.getChannel().size() == 0) {
                String header = "\"timestamp\",\"endpoint\",\"payload\",\"error\"\n";
                fos.write(header.getBytes(StandardCharsets.UTF_8));
            }
            fos.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
