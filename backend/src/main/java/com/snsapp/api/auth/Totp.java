package com.snsapp.api.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * 認証アプリの6桁のコード(TOTP、RFC 6238)。HMAC-SHA1・30秒ごと・6桁(Google Authenticator などの既定)。
 * 時計のずれを考えて、前後1つ(±30秒)の時間枠まで受け付ける
 */
public final class Totp {

    public static final int PERIOD_SECONDS = 30;
    public static final int DIGITS = 6;
    private static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 新しい秘密の鍵(Base32、32文字) */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long stepAt(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), PERIOD_SECONDS);
    }

    /** その時間枠のコード(6桁、先頭の0も付ける) */
    public static String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(fromBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * code が合う時間枠を返す。合わなければ -1。
     * afterStep 以前の時間枠は受け付けない(一度使ったコードを、盗み見た人がもう一度使えないように)
     */
    public static long match(String secret, String code, Instant now, long afterStep) {
        if (code == null || !code.matches("[0-9]{" + DIGITS + "}")) {
            return -1;
        }
        long current = stepAt(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (step > afterStep && constantTimeEquals(code(secret, step), code)) {
                return step;
            }
        }
        return -1;
    }

    /** 認証アプリに読ませる URI(QR コードにする) */
    public static String uri(String issuer, String account, String secret) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] fromBase32(String text) {
        String s = text.replace("=", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(s.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : s.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Base32 ではない文字: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
