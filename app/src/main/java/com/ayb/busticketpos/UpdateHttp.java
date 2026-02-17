package com.ayb.busticketpos;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public final class UpdateHttp {
    private UpdateHttp(){}

    public static String postJson(String urlStr, String jsonBody, Map<String,String> headers) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");

        if (headers != null) {
            for (Map.Entry<String,String> e : headers.entrySet()) {
                conn.setRequestProperty(e.getKey(), e.getValue());
            }
        }

        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + body);
        return body;
    }

    public static void downloadToFile(String urlStr, File outFile) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("Download failed HTTP " + code);

        try (InputStream in = conn.getInputStream();
             FileOutputStream fos = new FileOutputStream(outFile)) {
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
}
