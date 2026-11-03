package com.zrlog.admin.business.automation;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.zrlog.data.security.AccountAccess;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class TaskRegistryTest {
    @Test
    public void lookupLoadsOnlyTheRequestedProviderAndRefreshesBetweenOperations() {
        AtomicInteger requested = new AtomicInteger();
        AtomicInteger unrelated = new AtomicInteger();
        var registry =
                new TaskRegistry(
                        () ->
                                List.of(
                                        provider("test.reminder", requested),
                                        provider("test.ai", unrelated)));
        var lookup = registry.lookup();
        assertNotNull(lookup.provider("test.reminder"));
        assertEquals(0, requested.get());
        assertSame(lookup.require("test.reminder"), lookup.require("test.reminder"));
        assertEquals(1, requested.get());
        assertEquals(0, unrelated.get());
        registry.require("test.reminder");
        assertEquals(2, requested.get());
        assertEquals(0, unrelated.get());
        assertEquals(2, registry.definitions().size());
        assertEquals(3, requested.get());
        assertEquals(1, unrelated.get());
    }

    @Test
    public void duplicateRegistrationAndMismatchedDefinitionsFailBeforeExecution() {
        var duplicate =
                new TaskRegistry(
                        () ->
                                List.of(
                                        provider("test.same", new AtomicInteger()),
                                        provider("test.same", new AtomicInteger())));
        assertThrows(IllegalStateException.class, duplicate::lookup);
        var missing = new TaskRegistry(() -> List.of(new ReminderTaskProvider()));
        assertThrows(IllegalArgumentException.class, () -> missing.require("test.missing"));
        var mismatch =
                new TaskRegistry(
                        () ->
                                List.of(
                                        provider(
                                                List.of("test.one"),
                                                List.of("test.two"),
                                                new AtomicInteger())));
        assertThrows(IllegalStateException.class, () -> mismatch.require("test.one"));
        var incomplete =
                new TaskRegistry(
                                () ->
                                        List.of(
                                                provider(
                                                        List.of("test.one", "test.two"),
                                                        List.of("test.one"),
                                                        new AtomicInteger())))
                        .lookup();
        assertThrows(IllegalStateException.class, () -> incomplete.require("test.one"));
        assertThrows(IllegalStateException.class, () -> incomplete.require("test.one"));
    }

    private ScheduledTaskProvider provider(String type, AtomicInteger loads) {
        return provider(List.of(type), List.of(type), loads);
    }

    private ScheduledTaskProvider provider(
            List<String> registered, List<String> defined, AtomicInteger loads) {
        return new ScheduledTaskProvider() {
            public List<String> types() {
                return registered;
            }

            public List<AutomationModels.Definition> definitions() {
                loads.incrementAndGet();
                var definitions = new java.util.ArrayList<AutomationModels.Definition>();
                for (String type : defined) {
                    var definition = new AutomationModels.Definition();
                    definition.type = type;
                    definitions.add(definition);
                }
                return definitions;
            }

            public JsonObject prepare(String type, JsonObject parameters, AccountAccess actor) {
                return parameters;
            }

            public AutomationModels.Result execute(
                    String type, JsonObject parameters, TaskExecutionContext context) {
                throw new AssertionError();
            }
        };
    }
}
