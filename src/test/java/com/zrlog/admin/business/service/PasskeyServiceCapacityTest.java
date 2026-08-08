package com.zrlog.admin.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.webauthn4j.verifier.exception.MaliciousCounterValueException;
import com.zrlog.admin.business.exception.PasskeyLimitExceededException;
import com.zrlog.admin.business.exception.PasskeyRequestBusyException;
import com.zrlog.admin.business.exception.PasskeyVerificationException;
import com.zrlog.admin.business.rest.response.PasskeyOptionsResponse;
import com.zrlog.admin.business.rest.response.PasskeySummaryResponse;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.support.TestLogCapture;
import com.zrlog.model.UserPasskey;
import com.zrlog.model.UserPasskeyChallenge;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PasskeyServiceCapacityTest {

    @Test
    public void shouldSerializeAuthenticationChallengeCleanupCountAndInsert() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            long now = System.currentTimeMillis();
            assertTrue(new UserPasskeyChallenge().save("expired", "authentication", null,
                    "{}", now - 1, now - 1_000));
            PasskeyService service = new PasskeyService(1);

            List<Object> results = runConcurrently(
                    () -> startAuthentication(service),
                    () -> startAuthentication(service));

            assertEquals(1, results.stream().filter(PasskeyOptionsResponse.class::isInstance).count());
            assertEquals(1, results.stream().filter(PasskeyRequestBusyException.class::isInstance).count());
            assertEquals(1L, ((Number) db.scalar(
                    "select count(1) from user_passkey_challenge where ceremony=?", "authentication")).longValue());
            assertEquals(0L, ((Number) db.scalar(
                    "select count(1) from website where name like 'distributed_lock_passkey-%'")).longValue());
        }
    }

    @Test
    public void shouldKeepConcurrentRegistrationAtPerUserLimit() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            UserPasskey passkeyDao = new UserPasskey();
            for (int index = 0; index < 9; index++) {
                assertTrue(passkeyDao.save(1, "seed-hash-" + index, "seed-id-" + index,
                        "seed-public-key", 0, "", "Seed " + index, "seed-aaguid", true, false,
                        "http://localhost:18080", "localhost", System.currentTimeMillis()));
            }
            PasskeyService service = new PasskeyService(4);
            PasskeyService.ChallengeState state = new PasskeyService.ChallengeState(
                    "challenge", "localhost", "http://localhost:18080", "user-handle", "Concurrent passkey");

            List<Object> results = runConcurrently(
                    () -> persistRegistration(service, state, "concurrent-first"),
                    () -> persistRegistration(service, state, "concurrent-second"));

            assertEquals(1, results.stream().filter(PasskeySummaryResponse.class::isInstance).count());
            assertEquals(1, results.stream().filter(PasskeyLimitExceededException.class::isInstance).count());
            assertEquals(10L, passkeyDao.countByUserId(1));
            assertEquals(0L, ((Number) db.scalar(
                    "select count(1) from website where name like 'distributed_lock_passkey-%'")).longValue());
        }
    }

    @Test
    public void shouldLogVerificationFailuresWithoutExposingTheirCause() {
        try (TestLogCapture logs = TestLogCapture.forClass(PasskeyService.class)) {
            PasskeyVerificationException ordinary = PasskeyService.verificationFailure(
                    "authentication", new IllegalStateException("broken parser"));
            PasskeyVerificationException counter = PasskeyService.verificationFailure(
                    "authentication", new MaliciousCounterValueException("counter rollback"));

            assertNull(ordinary.getCause());
            assertNull(counter.getCause());
            assertTrue(logs.contains(Level.FINE, "Passkey authentication verification failed"));
            assertTrue(logs.contains(Level.WARNING, "non-increasing signature counter"));
        }
    }

    private static Object startAuthentication(PasskeyService service) throws Exception {
        try {
            return service.startAuthentication(request());
        } catch (PasskeyRequestBusyException e) {
            return e;
        }
    }

    private static Object persistRegistration(PasskeyService service, PasskeyService.ChallengeState state,
                                              String credentialId) throws Exception {
        try {
            return service.persistRegistrationCredential(1,
                    new PasskeyService.StoredPasskey(credentialId.getBytes(StandardCharsets.US_ASCII), "public-key", 0,
                            "", "aaguid", true, false), state);
        } catch (PasskeyLimitExceededException e) {
            return e;
        }
    }

    private static List<Object> runConcurrently(Callable<Object> first, Callable<Object> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> firstResult = executor.submit(awaitStart(ready, start, first));
            Future<Object> secondResult = executor.submit(awaitStart(ready, start, second));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Object> awaitStart(CountDownLatch ready, CountDownLatch start,
                                               Callable<Object> operation) {
        return () -> {
            ready.countDown();
            start.await();
            return operation.call();
        };
    }

    private static HttpRequest request() {
        return (HttpRequest) Proxy.newProxyInstance(
                PasskeyServiceCapacityTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        if ("Origin".equals(args[0])) {
                            return "http://localhost:18080";
                        }
                        if ("Host".equals(args[0])) {
                            return "localhost:18080";
                        }
                    }
                    if ("getScheme".equals(method.getName())) {
                        return "http";
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }
}
