package com.zrlog.admin.business.ai.service;

import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AIServiceUtilityTest {

    @Test
    public void shouldResolveImageSizeExtensionMimeAndDataUrl() {
        AIImageService service = new AIImageService();

        assertEquals("1024x1024", service.resolveImageRequestSize("1:1"));
        assertEquals("1536x1024", service.resolveImageRequestSize("16:9"));
        assertEquals("1536x1024", service.resolveImageRequestSize("3:4"));
        assertEquals("jpg", service.toExtension("image/jpeg", ""));
        assertEquals("webp", service.toExtension("image/webp", ""));
        assertEquals("png", service.toExtension("", "https://example.com/a.png?x=1"));
        assertEquals("jpg", service.toExtension("", "https://example.com/a.jpeg"));
        assertEquals("png", service.toExtension("", "https://example.com/no-ext"));
        assertEquals("image/jpeg", service.toMimeType("jpg", ""));
        assertEquals("image/webp", service.toMimeType("webp", ""));
        assertEquals("image/png", service.toMimeType("png", ""));
        assertEquals("image/custom", service.toMimeType("png", "image/custom;charset=utf-8"));
        assertEquals("abc", service.truncate("abc", 10));
        assertEquals("abc", service.truncate("abcdef", 3));

        AIImageService.DecodedImage decoded = service.decodeDataUrl("data:image/webp;base64,YWJj", "");
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), decoded.bytes);
        assertEquals("webp", decoded.extension);

        AIImageService.DecodedImage fallbackDecoded = service.decodeDataUrl("YWJj", "jpg");
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), fallbackDecoded.bytes);
        assertEquals("jpg", fallbackDecoded.extension);
    }

    @Test
    public void shouldRejectInvalidDataUrl() {
        AIImageService service = new AIImageService();

        try {
            service.decodeDataUrl("data:image/png;base64", "");
        } catch (ArgsException e) {
            assertTrue(e.getMessage().contains("dataUrl"));
            return;
        }
        throw new AssertionError("Expected ArgsException");
    }

    @Test
    public void shouldExposeChatUtilityBehavior() throws Exception {
        AIChatService service = new AIChatService();

        assertEquals("length", service.normalizeFinishReason(" Length "));
        assertEquals("provider error", service.toProviderStreamErrorDetail(Map.of("message", "provider error")));
        assertEquals("fallback", service.toProviderStreamErrorDetail("fallback"));
        assertEquals("line1line2", service.readErrorBody(
                new ByteArrayInputStream("line1\nline2".getBytes(StandardCharsets.UTF_8))));
        assertEquals(AIChatService.ToolContextPolicy.FULL_CONVERSATION, service.getToolContextPolicy("title"));
        assertEquals(AIChatService.ToolContextPolicy.CHAT_ONLY, service.getToolContextPolicy("seo"));
        assertEquals("short", service.truncateContext("short"));
        assertEquals(500, service.truncateContext(repeat("a", 550)).length());
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }
}
