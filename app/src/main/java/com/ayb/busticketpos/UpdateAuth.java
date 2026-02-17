package com.ayb.busticketpos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class UpdateAuth {
    private UpdateAuth() {}

    public static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        return toHex(hash);
    }

    public static String sha256FileHex(java.io.File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) md.update(buf, 0, r);
        }
        return toHex(md.digest());
    }

    public static Map<String, String> buildAuthHeaders(int deviceId, String apiKey, byte[] bodyBytes) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis() / 1000L);
        String bodySha = sha256Hex(bodyBytes).toLowerCase(Locale.US);

        String base = deviceId + "." + ts + "." + bodySha;
        String sig = hmacSha256Hex(base, apiKey).toLowerCase(Locale.US);

        Map<String, String> h = new HashMap<>();
        h.put("X-Device-Id", String.valueOf(deviceId));
        h.put("X-Timestamp", ts);
        h.put("X-Body-Sha256", bodySha);
        h.put("X-Signature", sig);
        return h;
    }

    private static String hmacSha256Hex(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return toHex(out);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
