package com.ayb.busticketpos;

import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUtil {

    public static String get(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000); // 10s
        conn.setReadTimeout(10000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("GET failed: HTTP " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) result.append(line);
        reader.close();

        return result.toString();
    }

    public static String postJson(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(false); // we'll handle redirects manually
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        // Write body
        try (OutputStream os = conn.getOutputStream();
             OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(jsonBody);
            writer.flush();
        }

        int code;
        try {
            code = conn.getResponseCode();
        } catch (IOException ioe) {
            // TLS/DNS/socket errors land here -> bubble up with more context
            throw new IOException("POST connect/read failed: " + ioe.getClass().getSimpleName() + ": " + ioe.getMessage(), ioe);
        }

        // Handle 301/302/307/308
        if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == 307 || code == 308) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            if (loc == null) throw new IOException("Redirect without Location header");
            // Naive follow: repeat POST to new location
            return postJson(loc, jsonBody);
        }

        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
        }

        if (code < 200 || code >= 300) {

            throw new IOException("POST failed: HTTP " + code + " body=" + sb);
        }

        return sb.toString();
    }

}
