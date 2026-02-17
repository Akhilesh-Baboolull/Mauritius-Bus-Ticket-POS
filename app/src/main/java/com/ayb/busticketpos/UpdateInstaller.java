package com.ayb.busticketpos;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class UpdateInstaller {
    private UpdateInstaller(){}

    public static void installApk(Context ctx, File apk, int releaseId, int fromV, int toV) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        int sessionId = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(sessionId);

        try (OutputStream out = session.openWrite("app_update", 0, apk.length());
             InputStream in = new FileInputStream(apk)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            session.fsync(out);
        }

        // Pass metadata to receiver via extras
        Intent intent = new Intent(ctx, InstallResultReceiver.class);
        intent.putExtra("releaseId", releaseId);
        intent.putExtra("fromVersionCode", fromV);
        intent.putExtra("toVersionCode", toV);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                ctx, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        session.commit(pendingIntent.getIntentSender());
        session.close();
    }
}
