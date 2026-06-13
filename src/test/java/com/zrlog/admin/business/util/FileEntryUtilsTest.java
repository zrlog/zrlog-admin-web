package com.zrlog.admin.business.util;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileEntryUtilsTest {

    @Test
    public void shouldValidateExternalUrlHost() {
        assertTrue(FileEntryUtils.isExternalUrl("https://example.com/a.png"));
        assertTrue(FileEntryUtils.isExternalUrl("http://example.com/a.png"));
        assertTrue(FileEntryUtils.isExternalUrl("//cdn.example.com/a.png"));

        assertFalse(FileEntryUtils.isExternalUrl("https://"));
        assertFalse(FileEntryUtils.isExternalUrl("http://"));
        assertFalse(FileEntryUtils.isExternalUrl("//"));
        assertFalse(FileEntryUtils.isExternalUrl("not-url"));
    }

    @Test
    public void shouldExtractOnlyValidExternalMediaSrcValues() {
        Set<String> urls = FileEntryUtils.extractExternalResourceUrls(
                "<p><img src=\"https://example.com/a.png\"></p>"
                        + "<p><img src=\"//cdn.example.com/b.webp\"></p>");

        assertEquals(Set.of("https://example.com/a.png", "//cdn.example.com/b.webp"), urls);
    }

    @Test
    public void shouldIgnoreInvalidSrcInsteadOfContinuingToLaterTextUrl() {
        Set<String> urls = FileEntryUtils.extractExternalResourceUrls(
                "<p><img src=\"not-url\"> https://example.com/text.png</p>"
                        + "<p><img src=\"//\"></p>");

        assertEquals(Set.of(), urls);
    }
}
