package com.zrlog.admin.business.util;

import com.zrlog.admin.business.rest.response.FileEntryVO;
import com.zrlog.admin.business.type.FileEntryAccess;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FileEntryUtilsTest {

    @Test
    public void shouldDecorateMissingDirectoryAndFileEntries() {
        FileEntryVO missing = new FileEntryVO();
        missing.setMissing(true);
        missing.setImage(true);
        missing.setTextPreviewable(true);

        FileEntryUtils.decorateEntry(missing);

        assertFalse(missing.isImage());
        assertFalse(missing.isTextPreviewable());
        assertEquals("file", missing.getIconType());

        FileEntryVO virtualDirectory = new FileEntryVO("library", "/library", "directory", 0,
                null, 0);
        virtualDirectory.setVirtual(true);
        FileEntryUtils.decorateEntry(virtualDirectory);
        assertEquals("library", virtualDirectory.getIconType());

        FileEntryVO lockedDirectory = new FileEntryVO("private", "/private", "directory", 0,
                null, 0);
        lockedDirectory.setAccess(FileEntryAccess.ADMIN_ONLY);
        FileEntryUtils.decorateEntry(lockedDirectory);
        assertEquals("directory_locked", lockedDirectory.getIconType());

        FileEntryVO normalDirectory = new FileEntryVO("public", "/public", "directory", 0,
                null, 0);
        normalDirectory.setAccess(FileEntryAccess.PUBLIC_URL);
        FileEntryUtils.decorateEntry(normalDirectory);
        assertEquals("directory", normalDirectory.getIconType());
    }

    @Test
    public void shouldDecorateRegularFileEntryIconsByMimeTypeAndExtension() {
        FileEntryVO image = new FileEntryVO("cover.png", "/cover.png", "file", 10,
                "image/png", 0);
        FileEntryVO code = new FileEntryVO("app.tsx", "/app.tsx", "file", 10,
                "application/typescript-jsx", 0);
        FileEntryVO archiveByExtension = new FileEntryVO("backup.tar.gz", "/backup.tar.gz", "file", 10,
                "application/octet-stream", 0);
        FileEntryVO archiveByMime = new FileEntryVO("backup", "/backup", "file", 10,
                "application/zip", 0);
        FileEntryVO unknown = new FileEntryVO("binary.bin", "/binary.bin", "file", 10,
                null, 0);

        FileEntryUtils.decorateEntry(image);
        FileEntryUtils.decorateEntry(code);
        FileEntryUtils.decorateEntry(archiveByExtension);
        FileEntryUtils.decorateEntry(archiveByMime);
        FileEntryUtils.decorateEntry(unknown);

        assertTrue(image.isImage());
        assertEquals("image", image.getIconType());
        assertTrue(code.isTextPreviewable());
        assertEquals("code", code.getIconType());
        assertEquals("archive", archiveByExtension.getIconType());
        assertEquals("archive", archiveByMime.getIconType());
        assertEquals("file", unknown.getIconType());
    }

    @Test
    public void shouldResolveMimeTypesFromOverridesAndFileExtensions() {
        assertEquals("text/markdown", FileEntryUtils.toMimeType("README.md"));
        assertEquals("application/typescript-jsx", FileEntryUtils.toMimeType("/src/App.tsx?version=1#hash"));
        assertEquals("application/gzip", FileEntryUtils.toMimeType("backup.tar.gz"));
        assertEquals("text/css", FileEntryUtils.toMimeType("style.css"));
        assertEquals("application/octet-stream", FileEntryUtils.toMimeType("missing-extension"));
        assertEquals("application/octet-stream", FileEntryUtils.toMimeType(null));
    }

    @Test
    public void shouldValidateExternalUrlHost() {
        assertFalse(FileEntryUtils.isExternalUrl(null));
        assertFalse(FileEntryUtils.isExternalUrl("  "));
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
                        + "<p><img src=\"//cdn.example.com/b.webp\"></p>"
                        + "<video poster=\"https://example.com/poster.jpg\"></video>"
                        + "<object data=\"https://example.com/file.pdf\"></object>"
                        + "<a download href=\"https://example.com/archive.zip\">zip</a>"
                        + "<a href=\"https://example.com/readme.md\">md</a>"
                        + "<a href=\"https://example.com/page.html\">page</a>");

        assertEquals(Set.of(
                "https://example.com/a.png",
                "//cdn.example.com/b.webp",
                "https://example.com/poster.jpg",
                "https://example.com/file.pdf",
                "https://example.com/archive.zip",
                "https://example.com/readme.md"
        ), urls);
    }

    @Test
    public void shouldIgnoreInvalidSrcInsteadOfContinuingToLaterTextUrl() {
        assertEquals(Set.of(), FileEntryUtils.extractExternalResourceUrls(null));
        assertEquals(Set.of(), FileEntryUtils.extractExternalResourceUrls(""));

        Set<String> urls = FileEntryUtils.extractExternalResourceUrls(
                "<p><img src=\"not-url\"> https://example.com/text.png</p>"
                        + "<p><img src=\"//\"></p>");

        assertEquals(Set.of(), urls);
    }

    @Test
    public void shouldIdentifyLikelyExternalResourceUrls() {
        assertTrue(FileEntryUtils.isLikelyResourceUrl("https://example.com/file.avif?size=large"));
        assertTrue(FileEntryUtils.isLikelyResourceUrl("//cdn.example.com/app.msi#download"));
        assertFalse(FileEntryUtils.isLikelyResourceUrl("https://example.com/page.html"));
        assertFalse(FileEntryUtils.isLikelyResourceUrl("/local/file.png"));
    }
}
