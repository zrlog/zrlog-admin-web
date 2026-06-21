package com.zrlog.admin.business.rest.request;

import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CreateLinkRequestTest {

    @Test
    public void shouldAllowCommonSafeLinkSchemes() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setUrl(" https://example.com/a?q=1 ");
        request.doClean();
        assertEquals("https://example.com/a?q=1", request.getUrl());

        request.setUrl("/about");
        request.doClean();
        assertEquals("/about", request.getUrl());

        request.setUrl("//cdn.example.com/file.png");
        request.doClean();
        assertEquals("//cdn.example.com/file.png", request.getUrl());

        request.setUrl("mailto:support@example.com");
        request.doClean();
        assertEquals("mailto:support@example.com", request.getUrl());
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectJavascriptLinkScheme() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setUrl("javascript:alert(1)");
        request.doClean();
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectDataLinkScheme() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setUrl("data:text/html,<script>alert(1)</script>");
        request.doClean();
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectProtocolRelativeLinkWithoutHost() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setUrl("//");
        request.doClean();
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectHttpLinkWithoutHost() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setUrl("http:example");
        request.doClean();
    }

    @Test
    public void shouldStripHtmlFromLinkIcon() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setUrl("/about");
        request.setIcon("fa-link <img src=x onerror=alert(1)>");
        request.doClean();
        assertTrue(request.getIcon().contains("fa-link"));
        assertFalse(request.getIcon().contains("<"));
        assertFalse(request.getIcon().contains("onerror"));
    }
}
