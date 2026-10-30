package com.zrlog.admin.business.automation;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class TaskScheduleTest {
    @Test
    public void dailyScheduleUsesLocalTimeAcrossDaylightSavingTransitions() {
        AutomationModels.Schedule schedule = daily("America/New_York", "02:30");
        assertEquals(Long.valueOf(time("2026-03-08T07:30:00Z")), TaskSchedule.nextAfter(schedule, time("2026-03-08T05:00:00Z")));
        assertEquals(Long.valueOf(time("2026-03-09T06:30:00Z")), TaskSchedule.nextAfter(schedule, time("2026-03-08T07:30:00Z")));
        schedule.time = "01:30";
        assertEquals(Long.valueOf(time("2026-11-01T05:30:00Z")), TaskSchedule.nextAfter(schedule, time("2026-11-01T04:00:00Z")));
        // The repeated wall-clock hour does not publish the same day's article twice.
        assertEquals(Long.valueOf(time("2026-11-02T06:30:00Z")), TaskSchedule.nextAfter(schedule, time("2026-11-01T05:30:00Z")));
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
        AutomationModels.Schedule schedule = daily("Asia/Shanghai", "09:00");
        schedule.kind = "weekly";
        schedule.weekdays = List.of(1, 5);
        assertEquals(Long.valueOf(time("2026-10-30T01:00:00Z")), TaskSchedule.nextAfter(schedule, time("2026-10-29T00:00:00Z")));
        schedule.kind = "once";
        schedule.at = time("2026-10-30T01:00:00Z");
        assertNull(TaskSchedule.nextAfter(schedule, schedule.at));
    }

    @Test
    public void invalidTimezonesTimesAndIntervalsAreRejected() {
        AutomationModels.Schedule schedule = daily("UTC", "25:00");
        assertThrows(IllegalArgumentException.class, () -> TaskSchedule.validate(schedule));
        schedule.time = "09:00";
        schedule.timezone = "Mars/Olympus";
        assertThrows(IllegalArgumentException.class, () -> TaskSchedule.validate(schedule));
        schedule.timezone = "UTC";
        schedule.kind = "interval";
        schedule.at = 1L;
        schedule.intervalMinutes = 0;
        assertThrows(IllegalArgumentException.class, () -> TaskSchedule.validate(schedule));
    }

    private static AutomationModels.Schedule daily(String timezone, String time) {
        AutomationModels.Schedule schedule = new AutomationModels.Schedule();
        schedule.kind = "daily";
        schedule.timezone = timezone;
        schedule.time = time;
        return schedule;
    }

    private static long time(String value) { return Instant.parse(value).toEpochMilli(); }
}
