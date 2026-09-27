package com.zrlog.model;

import com.zrlog.admin.support.InMemoryZrLogDatabase;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UserPasskeyDatabaseTest {

    @Test
    public void shouldRejectExpiredChallengeAndConsumeValidChallengeOnlyOnce() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            UserPasskeyChallenge challenges = new UserPasskeyChallenge();
            long now = System.currentTimeMillis();

            assertTrue(challenges.save("expired-request", "authentication", null,
                    "{\"challenge\":\"expired\"}", now - 1, now - 1_000));
            assertNull(challenges.consume("expired-request", "authentication", now));

            assertTrue(challenges.save("valid-request", "authentication", 1,
                    "{\"challenge\":\"valid\"}", now + 60_000, now));
            Map<String, Object> consumed = challenges.consume("valid-request", "authentication", now);

            assertNotNull(consumed);
            assertEquals("{\"challenge\":\"valid\"}", consumed.get("requestJson"));
            assertNull(challenges.consume("valid-request", "authentication", now));
        }
    }

    @Test
    public void shouldConditionallyAdvanceSignatureCounterWithoutRollback() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open()) {
            UserPasskey passkeys = new UserPasskey();
            long now = System.currentTimeMillis();

            assertFalse(passkeys.hasAny());
            assertTrue(passkeys.save(1, "credential-hash", "credential-id", "public-key",
                    0, "internal", "Test passkey", "aaguid", true, false,
                    "https://example.com", "example.com", now));
            assertTrue(passkeys.hasAny());
            Map<String, Object> stored = passkeys.findByCredentialIdHash("credential-hash");
            long id = ((Number) stored.get("id")).longValue();

            assertTrue(passkeys.updateAfterAuthentication(id, 0, false, now + 1));
            assertTrue(passkeys.updateAfterAuthentication(id, 2, true, now + 2));
            assertFalse(passkeys.updateAfterAuthentication(id, 1, false, now + 3));

            Map<String, Object> updated = passkeys.findByCredentialIdHash("credential-hash");
            assertEquals(2L, ((Number) updated.get("signatureCount")).longValue());
            assertEquals(now + 2, ((Number) updated.get("lastUsedAt")).longValue());
            assertTrue(passkeys.deleteByIdAndUserId(id, 1));
            assertFalse(passkeys.hasAny());
        }
    }
}
