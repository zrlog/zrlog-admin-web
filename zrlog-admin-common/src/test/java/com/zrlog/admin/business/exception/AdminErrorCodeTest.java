package com.zrlog.admin.business.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class AdminErrorCodeTest {

    @Test
    public void shouldKeepLegacyCodesUnique() {
        Set<Integer> legacyCodes = new HashSet<>();

        for (AdminErrorCode code : AdminErrorCode.values()) {
            assertTrue(
                    "Duplicate legacy code " + code.getLegacyCode(),
                    legacyCodes.add(code.getLegacyCode()));
        }
    }

    @Test
    public void shouldExposeAdminErrorCodeFromExceptionBase() {
        AdminAuthException exception = new AdminAuthException();

        assertEquals(AdminErrorCode.AUTH_SESSION_EXPIRED.getLegacyCode(), exception.getError());
        assertEquals(AdminErrorCode.AUTH_SESSION_EXPIRED.getCode(), exception.getErrorCode());
    }
}
