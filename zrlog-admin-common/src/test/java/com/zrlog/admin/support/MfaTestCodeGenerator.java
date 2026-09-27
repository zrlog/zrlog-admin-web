package com.zrlog.admin.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Locale;

public final class MfaTestCodeGenerator {

    private MfaTestCodeGenerator() {
    }

    public static String currentCode(String secret) {
        long currentCounter = System.currentTimeMillis() / 1000 / 30;
        return code(secret, currentCounter);
    }

    public static String code(String secret, long counter) {
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
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException("Generate MFA test code failed", e);
        }
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
}
