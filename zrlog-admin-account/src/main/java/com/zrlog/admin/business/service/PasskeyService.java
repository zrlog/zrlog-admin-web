package com.zrlog.admin.business.service;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.*;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.MaliciousCounterValueException;
import com.zrlog.admin.business.AdminConstants;
import com.zrlog.admin.business.dto.UserLoginDTO;
import com.zrlog.admin.business.exception.PasskeyLimitExceededException;
import com.zrlog.admin.business.exception.PasskeyRequestBusyException;
import com.zrlog.admin.business.exception.PasskeyVerificationException;
import com.zrlog.admin.business.exception.PermissionErrorException;
import com.zrlog.admin.business.rest.request.*;
import com.zrlog.admin.business.rest.response.*;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.model.User;
import com.zrlog.model.UserPasskey;
import com.zrlog.model.UserPasskeyChallenge;
import com.zrlog.util.ZrLogUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PasskeyService {

    private static final String REGISTRATION = "registration";
    private static final String AUTHENTICATION = "authentication";
    private static final long CHALLENGE_TTL_MS = 2 * 60 * 1000L;
    private static final int MAX_PASSKEYS = 10;
    private static final int MAX_ACTIVE_AUTHENTICATION_CHALLENGES = 1024;
    private static final long LOCK_WAIT_SECONDS = 2L;
    private static final String AUTHENTICATION_CHALLENGE_LOCK = "passkey-authentication-challenges";
    private static final String REGISTRATION_LOCK_PREFIX = "passkey-registration-user-";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ObjectConverter OBJECT_CONVERTER = new ObjectConverter();
    private static final WebAuthnManager WEB_AUTHN_MANAGER =
            WebAuthnManager.createNonStrictWebAuthnManager(OBJECT_CONVERTER);
    private static final List<PublicKeyCredentialParameters> ES256_PARAMETERS = Collections.singletonList(
            new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256));
    private static final Logger LOGGER = LoggerUtil.getLogger(PasskeyService.class);

    private final Gson gson = new Gson();
    private final UserService userService = new UserService();
    private final PasskeyRequestContext requestContext = new PasskeyRequestContext();
    private final int maxActiveAuthenticationChallenges;

    public PasskeyService() {
        this(MAX_ACTIVE_AUTHENTICATION_CHALLENGES);
    }

    PasskeyService(int maxActiveAuthenticationChallenges) {
        if (maxActiveAuthenticationChallenges <= 0) {
            throw new IllegalArgumentException("maxActiveAuthenticationChallenges must be positive");
        }
        this.maxActiveAuthenticationChallenges = maxActiveAuthenticationChallenges;
    }

    public PasskeyOptionsResponse<PasskeyAuthenticationOptionsResponse> startAuthentication(HttpRequest request)
            throws SQLException {
        PasskeyRequestContext.Context context = requestContext.resolve(request);
        String challenge = randomBase64Url(32);
        String requestId = randomBase64Url(24);
        saveChallenge(requestId, AUTHENTICATION, null,
                new ChallengeState(challenge, context.getRpId(), context.getOrigin(), null, null));
        return new PasskeyOptionsResponse<>(requestId,
                new PasskeyAuthenticationOptionsResponse(challenge, context.getRpId()));
    }

    public UserLoginDTO finishAuthentication(PasskeyAuthenticationVerifyRequest verifyRequest, HttpRequest request)
            throws SQLException {
        PasskeyRequestContext.Context context = requestContext.resolve(request);
        ConsumedChallenge consumed = consumeChallenge(verifyRequest.getRequestId(), AUTHENTICATION, context);
        PasskeyCredential credential = verifyRequest.getResponse();
        try {
            validateCredentialEnvelope(credential);
            byte[] credentialId = decodeRequired(credential.getRawId());
            if (!MessageDigest.isEqual(credentialId, decodeRequired(credential.getId()))) {
                throw new PasskeyVerificationException();
            }
            UserPasskey passkeyDao = new UserPasskey();
            Map<String, Object> stored = passkeyDao.findByCredentialIdHash(sha256Hex(credentialId));
            if (stored == null || !MessageDigest.isEqual(credentialId,
                    decodeRequired(stringValue(stored, "credentialId")))) {
                throw new PasskeyVerificationException();
            }
            if (!Objects.equals(consumed.state.rpId, stringValue(stored, "rpId"))
                    || !Objects.equals(consumed.state.origin, stringValue(stored, "origin"))) {
                throw new PasskeyVerificationException();
            }
            int userId = intValue(stored, "userId");
            Map<String, Object> user = new User().loadById(userId);
            byte[] expectedUserHandle = decodeRequired(stringValue(user, "passkeyUserHandle"));
            byte[] submittedUserHandle = decodeRequired(credential.getResponse().getUserHandle());
            if (!MessageDigest.isEqual(expectedUserHandle, submittedUserHandle)) {
                throw new PasskeyVerificationException();
            }

            CredentialRecordImpl credentialRecord = restoreCredentialRecord(stored, credentialId);
            AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                    credentialId,
                    submittedUserHandle,
                    decodeRequired(credential.getResponse().getAuthenticatorData()),
                    decodeRequired(credential.getResponse().getClientDataJSON()),
                    gson.toJson(extensionResults(credential)),
                    decodeRequired(credential.getResponse().getSignature()));
            AuthenticationData verified = WEB_AUTHN_MANAGER.verify(authenticationRequest,
                    new AuthenticationParameters(serverProperty(consumed.state), credentialRecord,
                            Collections.singletonList(credentialId), true, true));
            if (!MessageDigest.isEqual(expectedUserHandle, verified.getUserHandle())) {
                throw new PasskeyVerificationException();
            }
            long counter = verified.getAuthenticatorData().getSignCount();
            boolean backupState = Boolean.TRUE.equals(credentialRecord.isBackedUp());
            if (!passkeyDao.updateAfterAuthentication(longValue(stored, "id"), counter,
                    backupState, System.currentTimeMillis())) {
                throw new PasskeyVerificationException();
            }
            return userService.buildLoginDTO(user);
        } catch (PasskeyVerificationException | PasskeyLimitExceededException | PasskeyRequestBusyException e) {
            throw e;
        } catch (RuntimeException e) {
            throw verificationFailure(AUTHENTICATION, e);
        }
    }

    public PasskeyOptionsResponse<PasskeyRegistrationOptionsResponse> startRegistration(
            int userId, PasskeyRegistrationOptionsRequest optionsRequest, HttpRequest request) throws SQLException {
        denyPreviewMode();
        userService.verifyCurrentCredentials(userId, optionsRequest.getPassword(), optionsRequest.getMfaCode());
        PasskeyRequestContext.Context context = requestContext.resolve(request);
        List<Map<String, Object>> existing = new UserPasskey().findByUserId(userId);
        if (existing.size() >= MAX_PASSKEYS) {
            throw new PasskeyLimitExceededException();
        }
        Map<String, Object> user = new User().loadById(userId);
        String userHandle = getOrCreateUserHandle(userId, user);
        String userName = Objects.toString(value(user, "userName"), "admin");
        String challenge = randomBase64Url(32);
        String requestId = randomBase64Url(24);
        String name = normalizeName(optionsRequest.getName());
        saveChallenge(requestId, REGISTRATION, userId,
                new ChallengeState(challenge, context.getRpId(), context.getOrigin(), userHandle, name));

        List<PasskeyCredentialDescriptor> excludeCredentials = existing.stream()
                .map(row -> new PasskeyCredentialDescriptor(stringValue(row, "credentialId"),
                        transportNames(stringValue(row, "transports"))))
                .collect(Collectors.toList());
        String rpName = Objects.toString(AdminConstants.getPublicWebSiteInfo().getTitle(), "ZrLog").trim();
        if (rpName.isEmpty()) {
            rpName = "ZrLog";
        }
        PasskeyRegistrationOptionsResponse options = new PasskeyRegistrationOptionsResponse(
                challenge,
                new PasskeyRegistrationOptionsResponse.RelyingParty(context.getRpId(), rpName),
                new PasskeyRegistrationOptionsResponse.User(userHandle, userName, userName),
                excludeCredentials);
        return new PasskeyOptionsResponse<>(requestId, options);
    }

    public PasskeySummaryResponse finishRegistration(int userId, PasskeyRegistrationVerifyRequest verifyRequest,
                                                     HttpRequest request) throws SQLException {
        denyPreviewMode();
        PasskeyRequestContext.Context context = requestContext.resolve(request);
        ConsumedChallenge consumed = consumeChallenge(verifyRequest.getRequestId(), REGISTRATION, context);
        if (!Objects.equals(consumed.userId, userId)) {
            throw new PasskeyVerificationException();
        }
        PasskeyCredential credential = verifyRequest.getResponse();
        try {
            validateCredentialEnvelope(credential);
            RegistrationRequest registrationRequest = new RegistrationRequest(
                    decodeRequired(credential.getResponse().getAttestationObject()),
                    decodeRequired(credential.getResponse().getClientDataJSON()),
                    gson.toJson(extensionResults(credential)),
                    new HashSet<>(safeList(credential.getResponse().getTransports())));
            RegistrationData verified = WEB_AUTHN_MANAGER.verify(registrationRequest,
                    new RegistrationParameters(serverProperty(consumed.state), ES256_PARAMETERS, true, true));
            AttestedCredentialData attestedCredentialData = Objects.requireNonNull(
                    Objects.requireNonNull(verified.getAttestationObject()).getAuthenticatorData()
                            .getAttestedCredentialData());
            byte[] credentialId = attestedCredentialData.getCredentialId();
            if (!MessageDigest.isEqual(credentialId, decodeRequired(credential.getRawId()))
                    || !MessageDigest.isEqual(credentialId, decodeRequired(credential.getId()))) {
                throw new PasskeyVerificationException();
            }
            Map<String, Object> user = new User().loadById(userId);
            if (!MessageDigest.isEqual(decodeRequired(consumed.state.userHandle),
                    decodeRequired(stringValue(user, "passkeyUserHandle")))) {
                throw new PasskeyVerificationException();
            }

            CredentialRecordImpl record = new CredentialRecordImpl(
                    verified.getAttestationObject(), verified.getCollectedClientData(),
                    verified.getClientExtensions(), verified.getTransports());
            StoredPasskey storedPasskey = new StoredPasskey(credentialId,
                    encode(OBJECT_CONVERTER.getCborConverter().writeValueAsBytes(attestedCredentialData.getCOSEKey())),
                    record.getCounter(), transportString(verified.getTransports()),
                    attestedCredentialData.getAaguid().toString(), Boolean.TRUE.equals(record.isBackupEligible()),
                    Boolean.TRUE.equals(record.isBackedUp()));
            return persistRegistrationCredential(userId, storedPasskey, consumed.state);
        } catch (PasskeyVerificationException | PasskeyLimitExceededException | PasskeyRequestBusyException e) {
            throw e;
        } catch (RuntimeException e) {
            throw verificationFailure(REGISTRATION, e);
        }
    }

    public List<PasskeySummaryResponse> list(int userId) throws SQLException {
        List<PasskeySummaryResponse> summaries = new ArrayList<>();
        for (Map<String, Object> row : new UserPasskey().findByUserId(userId)) {
            summaries.add(toSummary(row));
        }
        return summaries;
    }

    public void remove(int userId, PasskeyRemoveRequest removeRequest) throws SQLException {
        denyPreviewMode();
        userService.verifyCurrentCredentials(userId, removeRequest.getPassword(), removeRequest.getMfaCode());
        if (!new UserPasskey().deleteByIdAndUserId(removeRequest.getId(), userId)) {
            throw new PasskeyVerificationException();
        }
    }

    private CredentialRecordImpl restoreCredentialRecord(Map<String, Object> stored, byte[] credentialId) {
        COSEKey coseKey = OBJECT_CONVERTER.getCborConverter().readValue(
                decodeRequired(stringValue(stored, "publicKeyCose")), COSEKey.class);
        AttestedCredentialData attestedCredentialData = new AttestedCredentialData(
                new AAGUID(stringValue(stored, "aaguid")), credentialId, Objects.requireNonNull(coseKey));
        return new CredentialRecordImpl(null, true,
                booleanValue(stored, "backupEligible"), booleanValue(stored, "backupState"),
                longValue(stored, "signatureCount"), attestedCredentialData, null, null, null,
                transportSet(stringValue(stored, "transports")));
    }

    private ServerProperty serverProperty(ChallengeState state) {
        return ServerProperty.builder()
                .origin(new Origin(state.origin))
                .rpId(state.rpId)
                .challenge(new DefaultChallenge(decodeRequired(state.challenge)))
                .build();
    }

    private void validateCredentialEnvelope(PasskeyCredential credential) {
        if (credential == null || credential.getResponse() == null
                || !"public-key".equals(credential.getType())
                || StringUtils.isEmpty(credential.getId()) || StringUtils.isEmpty(credential.getRawId())) {
            throw new PasskeyVerificationException();
        }
    }

    private Map<String, Object> extensionResults(PasskeyCredential credential) {
        return credential.getClientExtensionResults() == null
                ? Collections.emptyMap() : credential.getClientExtensionResults();
    }

    private ConsumedChallenge consumeChallenge(String requestId, String ceremony,
                                               PasskeyRequestContext.Context context) throws SQLException {
        Map<String, Object> row = new UserPasskeyChallenge().consume(
                requestId, ceremony, System.currentTimeMillis());
        if (row == null) {
            throw new PasskeyVerificationException();
        }
        try {
            ChallengeState state = gson.fromJson(stringValue(row, "requestJson"), ChallengeState.class);
            if (state == null || StringUtils.isEmpty(state.challenge) || StringUtils.isEmpty(state.rpId)
                    || StringUtils.isEmpty(state.origin) || !Objects.equals(state.origin, context.getOrigin())
                    || !Objects.equals(state.rpId, context.getRpId())) {
                throw new PasskeyVerificationException();
            }
            Object userIdValue = value(row, "userId");
            Integer userId = userIdValue == null ? null : ((Number) userIdValue).intValue();
            return new ConsumedChallenge(userId, state);
        } catch (PasskeyVerificationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new PasskeyVerificationException();
        }
    }

    private void saveChallenge(String requestId, String ceremony, Integer userId, ChallengeState state)
            throws SQLException {
        long now = System.currentTimeMillis();
        String requestJson = gson.toJson(state);
        if (!AUTHENTICATION.equals(ceremony)) {
            if (!new UserPasskeyChallenge().save(
                    requestId, ceremony, userId, requestJson, now + CHALLENGE_TTL_MS, now)) {
                throw new PasskeyVerificationException();
            }
            return;
        }
        withLock(AUTHENTICATION_CHALLENGE_LOCK, () -> {
            UserPasskeyChallenge challengeDao = new UserPasskeyChallenge();
            challengeDao.deleteExpired(now);
            if (challengeDao.countActive(AUTHENTICATION, now) >= maxActiveAuthenticationChallenges) {
                throw new PasskeyRequestBusyException();
            }
            if (!challengeDao.insert(requestId, ceremony, userId, requestJson,
                    now + CHALLENGE_TTL_MS, now)) {
                throw new PasskeyVerificationException();
            }
            return null;
        });
    }

    PasskeySummaryResponse persistRegistrationCredential(int userId, StoredPasskey storedPasskey,
                                                         ChallengeState state) throws SQLException {
        return withLock(REGISTRATION_LOCK_PREFIX + userId, () -> {
            UserPasskey passkeyDao = new UserPasskey();
            if (passkeyDao.countByUserId(userId) >= MAX_PASSKEYS) {
                throw new PasskeyLimitExceededException();
            }
            long createdAt = System.currentTimeMillis();
            String credentialIdHash = sha256Hex(storedPasskey.credentialId);
            if (!passkeyDao.save(userId, credentialIdHash, encode(storedPasskey.credentialId),
                    storedPasskey.publicKeyCose, storedPasskey.signatureCount, storedPasskey.transports,
                    normalizeName(state.name), storedPasskey.aaguid, storedPasskey.backupEligible,
                    storedPasskey.backupState, state.origin, state.rpId, createdAt)) {
                throw new PasskeyVerificationException();
            }
            return toSummary(passkeyDao.findByCredentialIdHash(credentialIdHash));
        });
    }

    private <T> T withLock(String lockKey, LockedOperation<T> operation) throws SQLException {
        Lock lock = new DistributedLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new PasskeyRequestBusyException();
            }
            return operation.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PasskeyRequestBusyException();
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    static PasskeyVerificationException verificationFailure(String ceremony, RuntimeException exception) {
        if (exception instanceof MaliciousCounterValueException) {
            LOGGER.log(Level.WARNING,
                    "Passkey authentication rejected due to a non-increasing signature counter", exception);
        } else {
            LOGGER.log(Level.FINE, "Passkey " + ceremony + " verification failed", exception);
        }
        return new PasskeyVerificationException();
    }

    private String getOrCreateUserHandle(int userId, Map<String, Object> user) throws SQLException {
        String current = Objects.toString(value(user, "passkeyUserHandle"), "");
        if (StringUtils.isNotEmpty(current)) {
            return current;
        }
        String candidate = randomBase64Url(32);
        new User().execute("update user set passkeyUserHandle=? where userId=?"
                + " and (passkeyUserHandle is null or passkeyUserHandle='')", candidate, userId);
        return stringValue(new User().loadById(userId), "passkeyUserHandle");
    }

    private PasskeySummaryResponse toSummary(Map<String, Object> row) {
        if (row == null) {
            throw new PasskeyVerificationException();
        }
        Object lastUsedAt = value(row, "lastUsedAt");
        return new PasskeySummaryResponse(longValue(row, "id"), stringValue(row, "name"),
                longValue(row, "createdAt"), lastUsedAt == null ? null : ((Number) lastUsedAt).longValue(),
                transportNames(stringValue(row, "transports")));
    }

    private String normalizeName(String value) {
        String name = Objects.toString(value, "").trim();
        return name.isEmpty() ? "Passkey" : name;
    }

    private void denyPreviewMode() {
        if (ZrLogUtil.isPreviewMode()) {
            throw new PermissionErrorException();
        }
    }

    private static String randomBase64Url(int size) {
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return encode(bytes);
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] decodeRequired(String value) {
        if (StringUtils.isEmpty(value)) {
            throw new PasskeyVerificationException();
        }
        return Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String transportString(Set<AuthenticatorTransport> transports) {
        if (transports == null || transports.isEmpty()) {
            return "";
        }
        return transports.stream().map(AuthenticatorTransport::getValue).sorted().collect(Collectors.joining(","));
    }

    private static Set<AuthenticatorTransport> transportSet(String transports) {
        return transportNames(transports).stream().map(AuthenticatorTransport::create).collect(Collectors.toSet());
    }

    private static List<String> transportNames(String transports) {
        if (StringUtils.isEmpty(transports)) {
            return Collections.emptyList();
        }
        return Arrays.stream(transports.split(","))
                .map(String::trim).filter(item -> !item.isEmpty()).collect(Collectors.toList());
    }

    private static List<String> safeList(List<String> value) {
        return value == null ? Collections.emptyList() : value;
    }

    private static Object value(Map<String, Object> row, String key) {
        if (row == null) {
            return null;
        }
        Object value = row.get(key);
        if (value != null || row.containsKey(key)) {
            return value;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stringValue(Map<String, Object> row, String key) {
        return Objects.toString(value(row, key), "");
    }

    private static long longValue(Map<String, Object> row, String key) {
        return ((Number) Objects.requireNonNull(value(row, key))).longValue();
    }

    private static int intValue(Map<String, Object> row, String key) {
        return ((Number) Objects.requireNonNull(value(row, key))).intValue();
    }

    private static boolean booleanValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return "true".equalsIgnoreCase(Objects.toString(value, "")) || "1".equals(Objects.toString(value, ""));
    }

    public static class ChallengeState {
        private String challenge;
        private String rpId;
        private String origin;
        private String userHandle;
        private String name;

        ChallengeState(String challenge, String rpId, String origin, String userHandle, String name) {
            this.challenge = challenge;
            this.rpId = rpId;
            this.origin = origin;
            this.userHandle = userHandle;
            this.name = name;
        }
    }

    private static class ConsumedChallenge {
        private final Integer userId;
        private final ChallengeState state;

        private ConsumedChallenge(Integer userId, ChallengeState state) {
            this.userId = userId;
            this.state = state;
        }
    }

    static class StoredPasskey {
        private final byte[] credentialId;
        private final String publicKeyCose;
        private final long signatureCount;
        private final String transports;
        private final String aaguid;
        private final boolean backupEligible;
        private final boolean backupState;

        StoredPasskey(byte[] credentialId, String publicKeyCose, long signatureCount, String transports,
                      String aaguid, boolean backupEligible, boolean backupState) {
            this.credentialId = credentialId;
            this.publicKeyCose = publicKeyCose;
            this.signatureCount = signatureCount;
            this.transports = transports;
            this.aaguid = aaguid;
            this.backupEligible = backupEligible;
            this.backupState = backupState;
        }
    }

    @FunctionalInterface
    private interface LockedOperation<T> {
        T run() throws SQLException;
    }
}
