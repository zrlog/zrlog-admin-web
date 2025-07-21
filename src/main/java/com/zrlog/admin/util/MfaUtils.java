package com.zrlog.admin.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

public class MfaUtils {

    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int SECRET_SIZE = 20;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;

    private MfaUtils() {
    }

    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_SIZE];
        SECURE_RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isEmpty() || code == null) {
            return false;
        }
        String normalizedCode = code.trim();
        if (!normalizedCode.matches("\\d{6}")) {
            return false;
        }
        long currentCounter = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (generateCode(secret, currentCounter + offset).equals(normalizedCode)) {
                return true;
            }
        }
        return false;
    }

    public static String buildOtpAuthUrl(String issuer, String accountName, String secret) {
        String normalizedIssuer = issuer == null || issuer.isEmpty() ? "ZrLog Admin" : issuer;
        String normalizedAccount = accountName == null || accountName.isEmpty() ? "admin" : accountName;
        String label = urlEncode(normalizedIssuer + ":" + normalizedAccount);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(normalizedIssuer)
                + "&algorithm=SHA1&digits=" + CODE_DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    private static String generateCode(String secret, long counter) {
        byte[] secretBytes = base32Decode(secret);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Generate MFA code failed", e);
        }
    }

    private static String base32Encode(byte[] data) {
        StringBuilder builder = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer <<= 8;
            buffer |= value & 0xFF;
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                builder.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            builder.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return builder.toString();
    }

    private static byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] result = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char ch : normalized.toCharArray()) {
            int charValue = base32Value(ch);
            if (charValue < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer <<= 5;
            buffer |= charValue;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        if (index == result.length) {
            return result;
        }
        byte[] exact = new byte[index];
        System.arraycopy(result, 0, exact, 0, index);
        return exact;
    }

    private static int base32Value(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return ch - 'A';
        }
        if (ch >= '2' && ch <= '7') {
            return ch - '2' + 26;
        }
        return -1;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
