package com.zrlog.model;

import static org.junit.Assert.*;

import com.zrlog.admin.data.repository.UserPasskeyChallengeRepository;
import com.zrlog.admin.data.repository.UserPasskeyRepository;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.data.repository.*;
import org.junit.Test;

public class UserPasskeyDatabaseTest {

    @Test
    public void shouldRejectExpiredChallengeAndConsumeValidChallengeOnlyOnce() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            UserPasskeyChallengeRepository challenges =
                    new UserPasskeyChallengeRepository(ignored.dataSource());
            long now = System.currentTimeMillis();

            assertTrue(
                    challenges.save(
                            "expired-request",
                            "authentication",
                            null,
                            "{\"challenge\":\"expired\"}",
                            now - 1,
                            now - 1_000));
            assertNull(challenges.consume("expired-request", "authentication", now));

            assertTrue(
                    challenges.save(
                            "valid-request",
                            "authentication",
                            1,
                            "{\"challenge\":\"valid\"}",
                            now + 60_000,
                            now));
            UserPasskeyChallenge consumed =
                    challenges.consume("valid-request", "authentication", now);

            assertNotNull(consumed);
            assertEquals("{\"challenge\":\"valid\"}", consumed.getRequestJson());
            assertNull(challenges.consume("valid-request", "authentication", now));
        }
    }

    @Test
    public void shouldConditionallyAdvanceSignatureCounterWithoutRollback() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            UserPasskeyRepository passkeys = new UserPasskeyRepository(ignored.dataSource());
            long now = System.currentTimeMillis();

            assertFalse(passkeys.hasAny());
            assertTrue(
                    passkeys.save(
                            1,
                            "credential-hash",
                            "credential-id",
                            "public-key",
                            0,
                            "internal",
                            "Test passkey",
                            "aaguid",
                            true,
                            false,
                            "https://example.com",
                            "example.com",
                            now));
            assertTrue(passkeys.hasAny());
            UserPasskey stored = passkeys.findByCredentialIdHash("credential-hash");
            long id = ((Number) stored.getId()).longValue();

            assertTrue(passkeys.updateAfterAuthentication(stored, 0, false, now + 1));
            assertTrue(passkeys.updateAfterAuthentication(stored, 2, true, now + 2));
            assertFalse(passkeys.updateAfterAuthentication(stored, 1, false, now + 3));

            UserPasskey updated = passkeys.findByCredentialIdHash("credential-hash");
            assertEquals(2L, ((Number) updated.getSignatureCount()).longValue());
            assertEquals(now + 2, ((Number) updated.getLastUsedAt()).longValue());
            assertTrue(passkeys.deleteByIdAndUserId(id, 1));
            assertFalse(passkeys.hasAny());
        }
    }
}
