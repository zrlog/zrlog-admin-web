package com.zrlog.admin.business.automation;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TaskScheduleTest {
    private TimeZone originalTimezone;

    @Before
    public void rememberServerTimezone() {
        originalTimezone = TimeZone.getDefault();
    }

    @After
    public void restoreServerTimezone() {
        TimeZone.setDefault(originalTimezone);
    }

    @Test
    public void dailyScheduleUsesLocalTimeAcrossDaylightSavingTransitions() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        AutomationModels.Schedule schedule = daily("02:30");
        assertEquals(
                Long.valueOf(time("2026-03-08T07:30:00Z")),
                TaskSchedule.nextAfter(schedule, time("2026-03-08T05:00:00Z")));
        assertEquals(
                Long.valueOf(time("2026-03-09T06:30:00Z")),
                TaskSchedule.nextAfter(schedule, time("2026-03-08T07:30:00Z")));
        schedule.time = "01:30";
        assertEquals(
                Long.valueOf(time("2026-11-01T05:30:00Z")),
                TaskSchedule.nextAfter(schedule, time("2026-11-01T04:00:00Z")));
        // The repeated wall-clock hour does not publish the same day's article twice.
        assertEquals(
                Long.valueOf(time("2026-11-02T06:30:00Z")),
                TaskSchedule.nextAfter(schedule, time("2026-11-01T05:30:00Z")));
    }

    @Test
    public void missedIntervalsCoalesceWithoutChangingTheirAnchor() {
        AutomationModels.Schedule schedule = new AutomationModels.Schedule();
        schedule.kind = "interval";
        schedule.at = 1000L;
        schedule.intervalMinutes = 5;
        assertEquals(Long.valueOf(1000), TaskSchedule.nextAfter(schedule, 999));
        assertEquals(Long.valueOf(601000), TaskSchedule.nextAfter(schedule, 600999));
        assertEquals(Long.valueOf(901000), TaskSchedule.nextAfter(schedule, 601000));
    }

    @Test
    public void weeklyAndOneTimeSchedulesRetainTheirExactMeaning() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        AutomationModels.Schedule schedule = daily("09:00");
        schedule.kind = "weekly";
        schedule.weekdays = List.of(1, 5);
        assertEquals(
                Long.valueOf(time("2026-10-30T01:00:00Z")),
                TaskSchedule.nextAfter(schedule, time("2026-10-29T00:00:00Z")));
        schedule.kind = "once";
        schedule.at = time("2026-10-30T01:00:00Z");
        assertNull(TaskSchedule.nextAfter(schedule, schedule.at));
    }

    @Test
    public void invalidTimesAndIntervalsAreRejected() {
        AutomationModels.Schedule schedule = daily("25:00");
        assertThrows(IllegalArgumentException.class, () -> TaskSchedule.validate(schedule));
        schedule.time = "09:00";
        schedule.kind = "interval";
        schedule.at = 1L;
        schedule.intervalMinutes = 0;
        assertThrows(IllegalArgumentException.class, () -> TaskSchedule.validate(schedule));
    }

    @Test
    public void clientTimezoneCannotOverrideServerDailySchedule() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        var schedule =
                AutomationStore.JSON.fromJson(
                        "{\"kind\":\"daily\",\"time\":\"09:00\",\"timezone\":\"America/New_York\"}",
                        AutomationModels.Schedule.class);
        assertEquals(
                Long.valueOf(time("2026-11-05T01:00:00Z")),
                TaskSchedule.nextAfter(schedule, time("2026-11-05T00:00:00Z")));
        assertFalse(AutomationStore.JSON.toJson(schedule).contains("timezone"));
    }

    @Test
    public void localStartIsResolvedOnServerAndStoredAsAnAbsoluteInstant() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        var input = new AutomationModels.Schedule();
        input.atLocal = "2026-11-06T09:30";
        // Editing a plan replaces its previous timestamp with the newly entered server time.
        input.at = 1L;
        var schedule = TaskSchedule.normalize(input);
        assertEquals(Long.valueOf(time("2026-11-06T01:30:00Z")), schedule.at);
        assertNull(schedule.atLocal);
        assertEquals("2026-11-06T09:30", TaskSchedule.forDisplay(schedule).atLocal);
        assertEquals(schedule.at, TaskSchedule.normalize(TaskSchedule.forDisplay(schedule)).at);
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        assertEquals(schedule.at, TaskSchedule.nextAfter(schedule, schedule.at - 1));
        assertEquals("2026-11-05T20:30", TaskSchedule.forDisplay(schedule).atLocal);
        input.kind = "interval";
        input.intervalMinutes = 60;
        schedule = TaskSchedule.normalize(input);
        assertEquals(Long.valueOf(time("2026-11-06T14:30:00Z")), schedule.at);
        assertEquals(
                Long.valueOf(time("2026-11-06T15:30:00Z")),
                TaskSchedule.nextAfter(schedule, schedule.at));
        input.atLocal = "invalid";
        assertThrows(IllegalArgumentException.class, () -> TaskSchedule.normalize(input));
    }

    @Test
    public void editingAnUnchangedTimePreservesTheSecondDaylightSavingOccurrence() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        var schedule = new AutomationModels.Schedule();
        schedule.at = time("2026-11-01T06:30:45.123Z");
        var view = TaskSchedule.forDisplay(schedule);
        assertEquals("2026-11-01T01:30", view.atLocal);
        assertEquals(schedule.at, TaskSchedule.normalize(view).at);
    }

    private static AutomationModels.Schedule daily(String time) {
        AutomationModels.Schedule schedule = new AutomationModels.Schedule();
        schedule.kind = "daily";
        schedule.time = time;
        return schedule;
    }

    private static long time(String value) {
        return Instant.parse(value).toEpochMilli();
    }
}
