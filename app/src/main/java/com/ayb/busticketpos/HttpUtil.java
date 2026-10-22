package com.ayb.busticketpos;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Single HTTP layer for the app. Use postJson (with or without auth headers) and downloadToFile.
 * Log tag "HttpUtil" for debugging.
 */
public class HttpUtil {

    private static final String TAG = "HttpUtil";

    private static String previewBody(String body, int max) {
        if (body == null) return "(null)";
        String t = body.replace("\r", " ").replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }

    /**
     * PHP often prints warnings/HTML before {@code json_encode} output. Paths like {@code [ROOT]/api/...}
     * contain {@code '['}; we must NOT treat that as JSON array start — only {@code '{'} begins a
     * {@link JSONObject} for API replies.
     */
    public static String extractLeadingJsonObject(String raw) {
        if (raw == null) return "";
        int start = -1;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) == '{') {
                start = i;
                break;
            }
        }
        if (start < 0) return raw.trim();
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return raw.substring(start, i + 1);
                }
            }
        }
        return raw.substring(start).trim();
    }

    /** Parse JSON object after stripping HTML/PHP noise before the first {@code '{'}. */
    public static JSONObject parseJsonObjectLenient(String raw) throws JSONException {
        String json = extractLeadingJsonObject(raw);
        if (json.isEmpty()) throw new JSONException("empty body");
        return new JSONObject(json);
    }

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
        return postJson(urlStr, jsonBody, null);
    }

    /** POST JSON with optional headers (e.g. UpdateAuth.buildAuthHeaders for report/update endpoints). */
    public static String postJson(String urlStr, String jsonBody, Map<String, String> headers) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            conn.setDoOutput(true);
            conn.setDoInput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code;
            try {
                code = conn.getResponseCode();
            } catch (IOException ioe) {
                throw new IOException("POST connect/read failed: " + ioe.getClass().getSimpleName() + ": " + ioe.getMessage(), ioe);
            }

            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc == null) throw new IOException("Redirect without Location header");
                return postJson(loc, jsonBody, headers);
            }

            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String body = readAllUtf8(is);
            Log.i(TAG, "POST response url=" + urlStr + " code=" + code + " bytes=" + body.length()
                    + " preview=" + previewBody(body, 240));
            if (code < 200 || code >= 300) {
                throw new IOException("POST failed: HTTP " + code + " body=" + previewBody(body, 500));
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    /** Stream URL to file (e.g. APK download). */
    public static void downloadToFile(String urlStr, File outFile) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("Download failed HTTP " + code);
        try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) fos.write(buf, 0, r);
        }
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String readAllUtf8(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
