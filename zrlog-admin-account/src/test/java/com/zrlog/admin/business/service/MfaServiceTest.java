package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.InvalidMfaCodeException;
import com.zrlog.admin.business.exception.MfaCodeRequiredException;
import com.zrlog.admin.business.rest.request.UpdateMfaRequest;
import com.zrlog.admin.business.rest.response.MfaStatusResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.support.MfaTestCodeGenerator;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MfaServiceTest {

    @Test
    public void shouldReadMfaEnabledAcrossStorageRepresentations() {
        MfaService service = new MfaService();

        assertFalse(service.getMfaEnabled(new HashMap<>()));
        assertTrue(service.getMfaEnabled(user(true)));
        assertFalse(service.getMfaEnabled(user(false)));
        assertTrue(service.getMfaEnabled(user(1)));
        assertFalse(service.getMfaEnabled(user(0)));
        assertTrue(service.getMfaEnabled(user("1")));
        assertTrue(service.getMfaEnabled(user("true")));
        assertTrue(service.getMfaEnabled(user("TRUE")));
        assertFalse(service.getMfaEnabled(user("0")));
        assertFalse(service.getMfaEnabled(user(" true ")));
    }

    @Test
    public void shouldSkipLoginMfaWhenDisabled() {
        MfaService service = new MfaService();

        service.verifyLoginMfa(new HashMap<>(), null);
        service.verifyLoginMfa(user(false), "");
        service.verifyLoginMfa(user(0), "123456");
    }

    @Test
    public void shouldRequireLoginMfaCodeWhenEnabled() {
        MfaService service = new MfaService();

        assertThrows(MfaCodeRequiredException.class, () -> service.verifyLoginMfa(user(true), null));
        assertThrows(MfaCodeRequiredException.class, () -> service.verifyLoginMfa(user("1"), ""));
    }

    @Test
    public void shouldRejectMissingOrInvalidLoginMfaSecret() {
        MfaService service = new MfaService();
        Map<String, Object> missingSecret = user(true);
        Map<String, Object> invalidCode = user(true);
        invalidCode.put("mfaSecret", "JBSWY3DPEHPK3PXP");

        assertThrows(InvalidMfaCodeException.class, () -> service.verifyLoginMfa(missingSecret, "123456"));
        assertThrows(InvalidMfaCodeException.class, () -> service.verifyLoginMfa(invalidCode, "000000"));
    }

    @Test
    public void shouldCreateEnableAndDisableMfaThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            MfaService service = new MfaService();

            MfaStatusResponse initial = service.getMfaStatus(1);
            String secret = String.valueOf(db.queryOne(
                    "select mfaSecret from user where userId=?", 1).get("mfaSecret"));
            UpdateMfaRequest enableRequest = mfaRequest(currentCode(secret));

            assertFalse(initial.isEnabled());
            assertEquals("admin", initial.getAccountName());
            assertEquals("ZrLog Test", initial.getIssuer());
            assertEquals(secret, initial.getSecret());
            assertTrue(initial.getOtpauthUrl().startsWith("otpauth://totp/"));
            assertNotNull(secret);
            assertEquals(0, service.enableMfa(1, enableRequest).getError());
            assertTrue(service.isMfaEnabled(1));

            MfaStatusResponse enabled = service.getMfaStatus(1);
            assertTrue(enabled.isEnabled());
            assertEquals("", enabled.getOtpauthUrl());
            assertEquals(secret, enabled.getSecret());

            assertEquals(0, service.disableMfa(1, mfaRequest(currentCode(secret))).getError());
            assertFalse(service.isMfaEnabled(1));
            Map<String, Object> user = db.queryOne("select mfaEnabled, mfaSecret from user where userId=?", 1);
            assertEquals(false, service.getMfaEnabled(user));
            assertEquals(null, user.get("mfaSecret"));
        }
    }

    @Test
    public void shouldReturnSuccessWhenDisablingAlreadyDisabledMfaThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            MfaService service = new MfaService();

            assertEquals(0, service.disableMfa(1, mfaRequest("000000")).getError());
            assertFalse(service.isMfaEnabled(1));
        }
    }

    private static Map<String, Object> user(Object enabled) {
        Map<String, Object> user = new HashMap<>();
        user.put("mfaEnabled", enabled);
        return user;
    }

    private static UpdateMfaRequest mfaRequest(String code) {
        UpdateMfaRequest request = new UpdateMfaRequest();
        request.setCode(code);
        return request;
    }

    private static String currentCode(String secret) throws Exception {
        return MfaTestCodeGenerator.currentCode(secret);
    }
}
