package com.zrlog.admin.business.service;

import com.zrlog.admin.business.exception.InvalidMfaCodeException;
import com.zrlog.admin.business.exception.MfaCodeRequiredException;
import com.zrlog.admin.business.rest.request.UpdateMfaRequest;
import com.zrlog.admin.business.rest.response.MfaStatusResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.support.MfaTestCodeGenerator;
import com.zrlog.data.repository.UserRepository;
import org.junit.Test;
import com.zrlog.model.User;


import static org.junit.Assert.*;

public class MfaServiceTest {

    private static User user(Boolean enabled) {
        User user = new User();
        user.setMfaEnabled(enabled);
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

    @Test
    public void shouldReadMfaEnabledFromTypedUser() {
        MfaService service = new MfaService(() -> {
            throw new IllegalStateException("Database is unavailable in this test");
        });

        assertFalse(service.getMfaEnabled(null));
        assertFalse(service.getMfaEnabled(user(null)));
        assertTrue(service.getMfaEnabled(user(true)));
        assertFalse(service.getMfaEnabled(user(false)));
    }

    @Test
    public void shouldSkipLoginMfaWhenDisabled() {
        MfaService service = new MfaService(() -> {
            throw new IllegalStateException("Database is unavailable in this test");
        });

        service.verifyLoginMfa(user(null), null);
        service.verifyLoginMfa(user(false), "");
        service.verifyLoginMfa(user(false), "123456");
    }

    @Test
    public void shouldRequireLoginMfaCodeWhenEnabled() {
        MfaService service = new MfaService(() -> {
            throw new IllegalStateException("Database is unavailable in this test");
        });

        assertThrows(MfaCodeRequiredException.class, () -> service.verifyLoginMfa(user(true), null));
        assertThrows(MfaCodeRequiredException.class, () -> service.verifyLoginMfa(user(true), ""));
    }

    @Test
    public void shouldRejectMissingOrInvalidLoginMfaSecret() {
        MfaService service = new MfaService(() -> {
            throw new IllegalStateException("Database is unavailable in this test");
        });
        User missingSecret = user(true);
        User invalidCode = user(true);
        invalidCode.setMfaSecret("JBSWY3DPEHPK3PXP");

        assertThrows(InvalidMfaCodeException.class, () -> service.verifyLoginMfa(missingSecret, "123456"));
        assertThrows(InvalidMfaCodeException.class, () -> service.verifyLoginMfa(invalidCode, "000000"));
    }

    @Test
    public void shouldCreateEnableAndDisableMfaThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            MfaService service = new MfaService(() -> new UserRepository(db.dataSource()));

            MfaStatusResponse initial = service.getMfaStatus(1, db.unitCacheService().getPublicWebSiteInfo().getTitle());
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

            MfaStatusResponse enabled = service.getMfaStatus(1, db.unitCacheService().getPublicWebSiteInfo().getTitle());
            assertTrue(enabled.isEnabled());
            assertEquals("", enabled.getOtpauthUrl());
            assertEquals(secret, enabled.getSecret());

            assertEquals(0, service.disableMfa(1, mfaRequest(currentCode(secret))).getError());
            assertFalse(service.isMfaEnabled(1));
            User user = new UserRepository(db.dataSource()).loadById(1L);
            assertEquals(false, service.getMfaEnabled(user));
            assertEquals(null, user.getMfaSecret());
        }
    }

    @Test
    public void shouldReturnSuccessWhenDisablingAlreadyDisabledMfaThroughRealUserTable() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            MfaService service = new MfaService(() -> new UserRepository(ignored.dataSource()));

            assertEquals(0, service.disableMfa(1, mfaRequest("000000")).getError());
            assertFalse(service.isMfaEnabled(1));
        }
    }
}
