package com.zrlog.admin.business.rest.request;

import com.zrlog.common.exception.ArgsException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CreateNavRequestTest {

    @Test
    public void shouldAllowCommonSafeNavSchemes() {
        CreateNavRequest request = new CreateNavRequest();
        request.setUrl(" /archive ");
        request.doClean();
        assertEquals("/archive", request.getUrl());

        request.setUrl("https://example.com/docs");
        request.doClean();
        assertEquals("https://example.com/docs", request.getUrl());

        request.setUrl("//cdn.example.com/page");
        request.doClean();
        assertEquals("//cdn.example.com/page", request.getUrl());
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectJavascriptNavScheme() {
        CreateNavRequest request = new CreateNavRequest();
        request.setUrl("javascript:alert(1)");
        request.doClean();
    }

    @Test(expected = ArgsException.class)
    public void shouldRejectDataNavScheme() {
        CreateNavRequest request = new CreateNavRequest();
        request.setUrl("data:text/html,<script>alert(1)</script>");
        request.doClean();
    }

    @Test
    public void shouldStripHtmlFromNavIcon() {
        CreateNavRequest request = new CreateNavRequest();
        request.setUrl("/archive");
        request.setIcon("fa-nav <svg onload=alert(1)></svg>");
        request.doClean();
        assertTrue(request.getIcon().contains("fa-nav"));
        assertFalse(request.getIcon().contains("<"));
        assertFalse(request.getIcon().contains("onload"));
    }
}
