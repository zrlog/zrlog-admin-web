package com.zrlog.admin.util;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MfaUtilsTest {

    @Test
    public void shouldGenerateBase32Secret() {
        String secret = MfaUtils.generateSecret();

        assertTrue(secret.matches("[A-Z2-7]{32}"));
    }

    @Test
    public void shouldRejectInvalidMfaCodeInputs() {
        assertFalse(MfaUtils.verifyCode(null, "123456"));
        assertFalse(MfaUtils.verifyCode("", "123456"));
        assertFalse(MfaUtils.verifyCode("JBSWY3DPEHPK3PXP", null));
        assertFalse(MfaUtils.verifyCode("JBSWY3DPEHPK3PXP", "12345"));
        assertFalse(MfaUtils.verifyCode("JBSWY3DPEHPK3PXP", "abcdef"));
    }

    @Test
    public void shouldVerifyCurrentWindowCode() throws Exception {
        String secret = "JBSWY3DPEHPK3PXP";
        long currentCounter = System.currentTimeMillis() / 1000 / 30;
        Method method = MfaUtils.class.getDeclaredMethod("generateCode", String.class, long.class);
        method.setAccessible(true);
        String code = (String) method.invoke(null, secret, currentCounter);

        assertTrue(MfaUtils.verifyCode(secret, " " + code + " "));
    }

    @Test
    public void shouldBuildOtpAuthUrlWithDefaultsAndEncoding() {
        String url = MfaUtils.buildOtpAuthUrl(null, "admin user", "ABC 123");

        assertTrue(url.startsWith("otpauth://totp/ZrLog%20Admin%3Aadmin%20user?"));
        assertTrue(url.contains("secret=ABC%20123"));
        assertTrue(url.contains("issuer=ZrLog%20Admin"));
        assertTrue(url.contains("digits=6"));
        assertTrue(url.contains("period=30"));
    }
}
