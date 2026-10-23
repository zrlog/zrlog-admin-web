package com.zrlog.admin.business.ai.service;

import com.google.gson.Gson;
import com.zrlog.admin.business.ai.model.AIChatModels.Event;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Encodes the existing browser event protocol. */
public class AIChatSseWriter {
    private final Gson gson = new Gson();

    @Inject
    public AIChatSseWriter() {
    }

    void write(OutputStream out, Event event) throws IOException {
        out.write(("data: " + gson.toJson(event) + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
