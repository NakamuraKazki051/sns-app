package com.snsapp.api.auth;

/**
 * ブラウザの User-Agent を「Chrome(Windows)」のような短い名前にする。ログイン中の端末の一覧に出す。
 * 細かい版は出さない(どの端末か見分けられれば十分。User-Agent は偽れるので、目安にとどまる)
 */
public final class UserAgents {

    static final String UNKNOWN = "不明な端末";

    private UserAgents() {
    }

    public static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String browser = browser(userAgent);
        String os = os(userAgent);
        if (browser == null && os == null) {
            return UNKNOWN;
        }
        if (browser == null) {
            return os;
        }
        return os == null ? browser : browser + "(" + os + ")";
    }

    // 並びに意味がある。Edge・Opera・Samsung の UA には Chrome も、Chrome の UA には Safari も入っている
    private static String browser(String ua) {
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("SamsungBrowser/")) {
            return "Samsung Internet";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }
        if (ua.startsWith("curl/")) {
            return "curl";
        }
        return null;
    }

    private static String os(String ua) {
        if (ua.contains("iPhone")) {
            return "iPhone";
        }
        if (ua.contains("iPad")) {
            return "iPad";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "Mac";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return null;
    }
}
