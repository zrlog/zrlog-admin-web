package com.zrlog.admin.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.webauthn4j.converter.AttestationObjectConverter;
import com.webauthn4j.converter.AuthenticationExtensionsClientOutputsConverter;
import com.webauthn4j.converter.CollectedClientDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.AttestationObject;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.authenticator.Curve;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.attestation.statement.COSEKeyOperation;
import com.webauthn4j.data.attestation.statement.COSEKeyType;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.client.ClientDataType;
import com.webauthn4j.data.client.CollectedClientData;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import com.webauthn4j.data.extension.client.AuthenticationExtensionsClientOutputs;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WebAuthnNativeImageMetadataTest {

    private static final String REFLECT_CONFIG =
            "META-INF/native-image/com.hibegin/zrlog-admin-web/reflect-config.json";

    @Test
    public void shouldKeepWebAuthnReflectionMetadataNarrowAndResolvable() throws Exception {
        JsonArray entries = loadReflectConfig();

        assertEquals(11, entries.size());
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            assertFalse(entry.has("allDeclaredConstructors"));
            assertFalse(entry.has("allDeclaredFields"));
            assertFalse(entry.has("allDeclaredMethods"));
            assertFalse(entry.has("allPublicConstructors"));
            assertFalse(entry.has("allPublicFields"));
            assertFalse(entry.has("allPublicMethods"));
            resolveConfiguredMembers(entry);
        }

        assertConfiguredMethod(entries, "com.webauthn4j.data.client.CollectedClientData", "<init>",
                "com.webauthn4j.data.client.ClientDataType",
                "com.webauthn4j.data.client.challenge.Challenge",
                "com.webauthn4j.data.client.Origin", "java.lang.Boolean",
                "com.webauthn4j.data.client.Origin", "com.webauthn4j.data.client.TokenBinding");
        assertConfiguredMethod(entries, "com.webauthn4j.data.client.ClientDataType", "create",
                "java.lang.String");
        assertConfiguredMethod(entries, "com.webauthn4j.data.client.Origin", "deserialize",
                "java.lang.String");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.extension.client.AuthenticationExtensionsClientOutputs", "<init>");
        assertConfiguredMethod(entries, "com.webauthn4j.data.attestation.AttestationObject", "<init>",
                "com.webauthn4j.data.attestation.authenticator.AuthenticatorData",
                "com.webauthn4j.data.attestation.statement.AttestationStatement");
        assertConfiguredField(entries, "com.webauthn4j.data.attestation.AttestationObject",
                "authenticatorData");
        assertConfiguredField(entries, "com.webauthn4j.data.attestation.AttestationObject",
                "attestationStatement");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.attestation.statement.NoneAttestationStatement", "<init>");
        assertConfiguredMethod(entries, "com.webauthn4j.data.attestation.authenticator.EC2COSEKey", "<init>",
                "byte[]", "com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier", "java.util.List",
                "com.webauthn4j.data.attestation.authenticator.Curve", "byte[]", "byte[]", "byte[]");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier", "deserialize", "long");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier", "getValue");
        assertConfiguredMethod(entries, "com.webauthn4j.data.attestation.authenticator.Curve",
                "deserialize", "int");
        assertConfiguredMethod(entries, "com.webauthn4j.data.attestation.authenticator.Curve", "getValue");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.attestation.statement.COSEKeyOperation", "deserialize", "int");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.attestation.statement.COSEKeyOperation", "getValue");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.attestation.statement.COSEKeyType", "deserialize", "int");
        assertConfiguredMethod(entries,
                "com.webauthn4j.data.attestation.statement.COSEKeyType", "getValue");
    }

    @Test
    public void shouldConvertPasskeyPayloadsCoveredByNativeMetadata() throws Exception {
        ObjectConverter objectConverter = new ObjectConverter();
        CollectedClientDataConverter clientDataConverter = new CollectedClientDataConverter(objectConverter);

        CollectedClientData createData = clientDataConverter.convert(clientDataJson("webauthn.create"));
        CollectedClientData getData = clientDataConverter.convert(clientDataJson("webauthn.get"));
        assertEquals(ClientDataType.WEBAUTHN_CREATE, createData.getType());
        assertEquals(ClientDataType.WEBAUTHN_GET, getData.getType());
        assertEquals("https://admin.example.com", createData.getOrigin().toString());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, createData.getChallenge().getValue());

        AuthenticationExtensionsClientOutputs<?> extensions =
                new AuthenticationExtensionsClientOutputsConverter(objectConverter).convert("{}");
        assertTrue(extensions.getKeys().isEmpty());

        EC2COSEKey coseKey = createEs256Key();
        byte[] coseBytes = objectConverter.getCborConverter().writeValueAsBytes(coseKey);
        COSEKey restoredCoseKey = objectConverter.getCborConverter().readValue(coseBytes, COSEKey.class);
        assertTrue(restoredCoseKey instanceof EC2COSEKey);
        EC2COSEKey restoredEc2Key = (EC2COSEKey) restoredCoseKey;
        assertEquals(COSEAlgorithmIdentifier.ES256, restoredEc2Key.getAlgorithm());
        assertEquals(COSEKeyType.EC2, restoredEc2Key.getKeyType());
        assertEquals(Curve.SECP256R1, restoredEc2Key.getCurve());
        assertEquals(Collections.singletonList(COSEKeyOperation.VERIFY), restoredEc2Key.getKeyOps());
        assertArrayEquals(coseKey.getX(), restoredEc2Key.getX());
        assertArrayEquals(coseKey.getY(), restoredEc2Key.getY());

        byte[] credentialId = new byte[]{9, 8, 7, 6};
        AttestedCredentialData credentialData = new AttestedCredentialData(AAGUID.ZERO, credentialId, coseKey);
        AuthenticatorData<RegistrationExtensionAuthenticatorOutput> authenticatorData =
                new AuthenticatorData<>(new byte[32],
                        (byte) (AuthenticatorData.BIT_UP | AuthenticatorData.BIT_AT), 0, credentialData);
        AttestationObject attestationObject =
                new AttestationObject(authenticatorData, new NoneAttestationStatement());
        AttestationObjectConverter attestationConverter = new AttestationObjectConverter(objectConverter);
        AttestationObject restoredAttestation =
                attestationConverter.convert(attestationConverter.convertToBytes(attestationObject));

        assertEquals("none", restoredAttestation.getFormat());
        assertTrue(restoredAttestation.getAttestationStatement() instanceof NoneAttestationStatement);
        assertArrayEquals(credentialId,
                restoredAttestation.getAuthenticatorData().getAttestedCredentialData().getCredentialId());
        assertTrue(restoredAttestation.getAuthenticatorData().getAttestedCredentialData().getCOSEKey()
                instanceof EC2COSEKey);
    }

    private static EC2COSEKey createEs256Key() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        EC2COSEKey generated = EC2COSEKey.create(keyPair, COSEAlgorithmIdentifier.ES256);
        return new EC2COSEKey(null, COSEAlgorithmIdentifier.ES256,
                Collections.singletonList(COSEKeyOperation.VERIFY), Curve.SECP256R1,
                generated.getX(), generated.getY(), null);
    }

    private static byte[] clientDataJson(String type) {
        return ("{\"type\":\"" + type + "\",\"challenge\":\"AQIDBA\","
                + "\"origin\":\"https://admin.example.com\",\"crossOrigin\":false}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static JsonArray loadReflectConfig() throws Exception {
        InputStream inputStream = WebAuthnNativeImageMetadataTest.class.getClassLoader()
                .getResourceAsStream(REFLECT_CONFIG);
        assertNotNull("Missing " + REFLECT_CONFIG, inputStream);
        try (InputStream stream = inputStream;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        }
    }

    private static void resolveConfiguredMembers(JsonObject entry) throws Exception {
        Class<?> type = Class.forName(entry.get("name").getAsString());
        JsonArray fields = entry.getAsJsonArray("fields");
        if (fields != null) {
            for (JsonElement field : fields) {
                type.getDeclaredField(field.getAsJsonObject().get("name").getAsString());
            }
        }
        JsonArray methods = entry.getAsJsonArray("methods");
        if (methods == null) {
            return;
        }
        for (JsonElement element : methods) {
            JsonObject configuredMethod = element.getAsJsonObject();
            String methodName = configuredMethod.get("name").getAsString();
            Class<?>[] parameterTypes = configuredMethod.getAsJsonArray("parameterTypes").asList().stream()
                    .map(JsonElement::getAsString)
                    .map(WebAuthnNativeImageMetadataTest::resolveType)
                    .toArray(Class<?>[]::new);
            if ("<init>".equals(methodName)) {
                Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
                assertTrue(type.getName() + " constructor must be a Jackson creator or no-arg constructor",
                        parameterTypes.length == 0 || constructor.isAnnotationPresent(JsonCreator.class));
            } else {
                Method method = type.getDeclaredMethod(methodName, parameterTypes);
                assertTrue(type.getName() + "#" + methodName + " must remain a Jackson creator or value",
                        method.isAnnotationPresent(JsonCreator.class)
                                || method.isAnnotationPresent(JsonValue.class));
            }
        }
    }

    private static Class<?> resolveType(String typeName) {
        switch (typeName) {
            case "byte":
                return byte.class;
            case "int":
                return int.class;
            case "long":
                return long.class;
            case "boolean":
                return boolean.class;
            default:
                if (typeName.endsWith("[]")) {
                    return Array.newInstance(resolveType(typeName.substring(0, typeName.length() - 2)), 0).getClass();
                }
                try {
                    return Class.forName(typeName);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Unknown reflection metadata type " + typeName, e);
                }
        }
    }

    private static void assertConfiguredMethod(JsonArray entries, String className,
                                               String methodName, String... parameterTypes) {
        JsonObject entry = findEntry(entries, className);
        for (JsonElement element : entry.getAsJsonArray("methods")) {
            JsonObject method = element.getAsJsonObject();
            if (methodName.equals(method.get("name").getAsString())
                    && method.getAsJsonArray("parameterTypes").equals(toJsonArray(parameterTypes))) {
                return;
            }
        }
        throw new AssertionError("Missing reflection metadata for " + className + "#" + methodName);
    }

    private static void assertConfiguredField(JsonArray entries, String className, String fieldName) {
        JsonObject entry = findEntry(entries, className);
        for (JsonElement element : entry.getAsJsonArray("fields")) {
            if (fieldName.equals(element.getAsJsonObject().get("name").getAsString())) {
                return;
            }
        }
        throw new AssertionError("Missing reflection metadata for " + className + "#" + fieldName);
    }

    private static JsonObject findEntry(JsonArray entries, String className) {
        for (JsonElement element : entries) {
            JsonObject entry = element.getAsJsonObject();
            if (className.equals(entry.get("name").getAsString())) {
                return entry;
            }
        }
        throw new AssertionError("Missing reflection metadata for " + className);
    }

    private static JsonArray toJsonArray(String[] values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
