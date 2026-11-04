package com.zrlog.admin.business.ai.service;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import com.zrlog.admin.business.ai.model.AIChatModels.Event;
import com.zrlog.admin.support.InMemoryZrLogDatabase;
import com.zrlog.admin.util.AdminLanguageContext;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class AIChatSseWriterTest {
    @Test
    public void errorEventsIncludeLocalizedMessagesAndKeepStableCodes() throws Exception {
        try (var db = InMemoryZrLogDatabase.open()) {
            for (String language : new String[] {"zh_CN", "en_US"}) {
                try (var locale = AdminLanguageContext.open(language)) {
                    Event event = new Event("error");
                    event.error = "contextChanged";
                    var out = new ByteArrayOutputStream();
                    new AIChatSseWriter().write(out, event);
                    var payload =
                            JsonParser.parseString(
                                            out.toString(StandardCharsets.UTF_8).substring(6))
                                    .getAsJsonObject();
                    assertEquals("contextChanged", payload.get("error").getAsString());
                    assertEquals(
                            "zh_CN".equals(language)
                                    ? "文章内容已变化，请基于当前内容重新发起请求"
                                    : "The article has changed. Start a new request using the current content",
                            payload.get("message").getAsString());
                    assertFalse(
                            AIChatErrorMessages.message("private diagnostic")
                                    .contains("private diagnostic"));
                }
            }
        }
    }
}
