package com.vikisol.one.session.util;

// Lightweight, dependency-free User-Agent -> friendly label parser (e.g. "Chrome on Windows",
// "Safari on iPhone") for the Active Sessions / My Security device list. Deliberately not a full
// UA-parsing library - covers the handful of browser/OS combinations that matter for "which of
// my devices is this" at a glance, not analytics-grade device fingerprinting.
public final class DeviceInfoParser {

    private DeviceInfoParser() {
    }

    public static String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "Unknown device";

        String browser = detectBrowser(userAgent);
        String os = detectOs(userAgent);
        if (browser == null && os == null) return "Unknown device";
        if (browser == null) return os;
        if (os == null) return browser;
        return browser + " on " + os;
    }

    private static String detectBrowser(String ua) {
        String u = ua.toLowerCase();
        if (u.contains("edg/")) return "Edge";
        if (u.contains("opr/") || u.contains("opera")) return "Opera";
        if (u.contains("chrome/") && !u.contains("chromium")) return "Chrome";
        if (u.contains("crios/")) return "Chrome";
        if (u.contains("fxios/") || u.contains("firefox/")) return "Firefox";
        if (u.contains("safari/") && !u.contains("chrome")) return "Safari";
        return null;
    }

    private static String detectOs(String ua) {
        String u = ua.toLowerCase();
        if (u.contains("iphone")) return "iPhone";
        if (u.contains("ipad")) return "iPad";
        if (u.contains("android")) return "Android";
        if (u.contains("windows")) return "Windows";
        if (u.contains("mac os x") || u.contains("macintosh")) return "macOS";
        if (u.contains("linux")) return "Linux";
        return null;
    }
}
