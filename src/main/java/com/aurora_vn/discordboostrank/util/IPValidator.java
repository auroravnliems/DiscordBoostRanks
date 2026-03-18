package com.aurora_vn.discordboostrank.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Validates that this plugin is running on the authorized server IP.
 * IP hash is baked in at build time — no plain-text IP stored.
 */
public class IPValidator {

    // SHA-256 hash của IP: 118.69.187.178
    private static final String ALLOWED_IP_HASH = "357db073044a7cb984135140c4c4f367bfe7ff58debe4ccdd02cce696be572e0";

    public static boolean isAuthorized() {
        try {
            if (matchesHash(getLocalIP())) return true;
            if (matchesHash(getPublicIP())) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static String getLocalIP() throws Exception {
        return InetAddress.getLocalHost().getHostAddress();
    }

    private static String getPublicIP() throws Exception {
        URL url = new URL("https://api.ipify.org");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            return reader.readLine().trim();
        }
    }

    private static boolean matchesHash(String ip) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(ip.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString().equals(ALLOWED_IP_HASH);
    }
}
