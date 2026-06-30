package com.zrlog.admin.business.ai.service;

import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AIPrivateUtilityTest {

    @Test
    public void shouldResolveImageSizeExtensionMimeAndDataUrl() throws Exception {
        AIImageService service = new AIImageService();

        assertEquals("1024x1024", invoke(service, "resolveImageRequestSize", "1:1"));
        assertEquals("1536x1024", invoke(service, "resolveImageRequestSize", "16:9"));
        assertEquals("1536x1024", invoke(service, "resolveImageRequestSize", "3:4"));
        assertEquals("jpg", invoke(service, "toExtension", "image/jpeg", ""));
        assertEquals("webp", invoke(service, "toExtension", "image/webp", ""));
        assertEquals("png", invoke(service, "toExtension", "", "https://example.com/a.png?x=1"));
        assertEquals("jpg", invoke(service, "toExtension", "", "https://example.com/a.jpeg"));
        assertEquals("png", invoke(service, "toExtension", "", "https://example.com/no-ext"));
        assertEquals("image/jpeg", invoke(service, "toMimeType", "jpg", ""));
        assertEquals("image/webp", invoke(service, "toMimeType", "webp", ""));
        assertEquals("image/png", invoke(service, "toMimeType", "png", ""));
        assertEquals("image/custom", invoke(service, "toMimeType", "png", "image/custom;charset=utf-8"));
        assertEquals("abc", invoke(service, "truncate", "abc", 10));
        assertEquals("abc", invoke(service, "truncate", "abcdef", 3));

        Object decoded = invoke(service, "decodeDataUrl", "data:image/webp;base64,YWJj", "");
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), (byte[]) field(decoded, "bytes"));
        assertEquals("webp", field(decoded, "extension"));

        Object fallbackDecoded = invoke(service, "decodeDataUrl", "YWJj", "jpg");
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), (byte[]) field(fallbackDecoded, "bytes"));
        assertEquals("jpg", field(fallbackDecoded, "extension"));
    }

    @Test
    public void shouldRejectInvalidDataUrl() throws Exception {
        AIImageService service = new AIImageService();
        Method method = AIImageService.class.getDeclaredMethod("decodeDataUrl", String.class, String.class);
        method.setAccessible(true);

        try {
            method.invoke(service, "data:image/png;base64", "");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof ArgsException);
            return;
        }
        throw new AssertionError("Expected ArgsException");
    }

    @Test
    public void shouldExposeChatPrivateUtilityBehavior() throws Exception {
        AIChatService service = new AIChatService();

        assertEquals("length", invoke(service, "normalizeFinishReason", " Length "));
        assertEquals("provider error", invoke(service, "toProviderStreamErrorDetail",
                Map.of("message", "provider error")));
        assertEquals("fallback", invoke(service, "toProviderStreamErrorDetail", "fallback"));
        assertEquals("line1line2", invoke(service, "readErrorBody",
                new ByteArrayInputStream("line1\nline2".getBytes(StandardCharsets.UTF_8))));
        assertEquals("FULL_CONVERSATION", invoke(service, "getToolContextPolicy", "title").toString());
        assertEquals("CHAT_ONLY", invoke(service, "getToolContextPolicy", "seo").toString());
        assertEquals("short", invoke(service, "truncateContext", "short"));
        assertEquals(500, invoke(service, "truncateContext", repeat("a", 550)).toString().length());
    }

    @Test
    public void shouldExposePrivateResultObjects() throws Exception {
        Class<?> streamResultClass = Class.forName(
                "com.zrlog.admin.business.ai.service.AIChatService$StreamReadResult");
        Method noFinishReason = streamResultClass.getDeclaredMethod("noFinishReason");
        noFinishReason.setAccessible(true);
        Object empty = noFinishReason.invoke(null);
        assertFalse((Boolean) invoke(empty, "hasFinishReason"));
        assertEquals("", invoke(empty, "getFinishReason"));
        assertFalse((Boolean) invoke(empty, "isNeedContinuation"));

        Constructor<?> streamConstructor = streamResultClass.getDeclaredConstructor(String.class, boolean.class);
        streamConstructor.setAccessible(true);
        Object continuable = streamConstructor.newInstance("length", true);
        assertTrue((Boolean) invoke(continuable, "hasFinishReason"));
        assertEquals("length", invoke(continuable, "getFinishReason"));
        assertTrue((Boolean) invoke(continuable, "isNeedContinuation"));

        Class<?> toolResultClass = Class.forName(
                "com.zrlog.admin.business.ai.service.AIChatService$ToolResult");
        Constructor<?> toolConstructor = toolResultClass.getDeclaredConstructor(String.class, Object.class);
        toolConstructor.setAccessible(true);
        Object toolResult = toolConstructor.newInstance("content", Map.of("key", "value"));
        assertEquals("content", field(toolResult, "content"));
        assertEquals(Map.of("key", "value"), field(toolResult, "payload"));

        Class<?> imageResultClass = Class.forName(
                "com.zrlog.admin.business.ai.service.AIImageService$ImageResult");
        Constructor<?> imageConstructor = imageResultClass.getDeclaredConstructor(byte[].class, String.class, String.class);
        imageConstructor.setAccessible(true);
        Object imageResult = imageConstructor.newInstance("img".getBytes(StandardCharsets.UTF_8), "png", "image/png");
        assertArrayEquals("img".getBytes(StandardCharsets.UTF_8), (byte[]) field(imageResult, "bytes"));
        assertEquals("png", field(imageResult, "extension"));
        assertEquals("image/png", field(imageResult, "mimeType"));
    }

    private static Object invoke(Object target, String methodName, Object... args) throws Exception {
        Method method = findMethod(target.getClass(), methodName, args);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Method findMethod(Class<?> type, String methodName, Object[] args) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                return method;
            }
        }
        throw new IllegalArgumentException("No method " + methodName + " on " + type);
    }

    private static Object field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }
}
