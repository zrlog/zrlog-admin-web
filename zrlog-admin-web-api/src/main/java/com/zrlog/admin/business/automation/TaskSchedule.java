package com.zrlog.admin.business.automation;

import java.time.*;
import java.util.Set;

/** Wall-clock schedules retain their timezone; intervals are anchored to their initial timestamp. */
public final class TaskSchedule {
    private TaskSchedule() { }

    public static void validate(AutomationModels.Schedule schedule) {
        if (schedule == null || schedule.kind == null || schedule.timezone == null) {
            throw new IllegalArgumentException("automation.invalidSchedule");
        }
        try {
            ZoneId.of(schedule.timezone);
            switch (schedule.kind) {
                case "once":
                    if (schedule.at == null || schedule.at <= 0) throw new IllegalArgumentException();
                    break;
                case "interval":
                    if (schedule.at == null || schedule.at <= 0 || schedule.intervalMinutes == null
                            || schedule.intervalMinutes < 1 || schedule.intervalMinutes > 525600) {
                        throw new IllegalArgumentException();
                    }
                    break;
                case "daily":
                case "weekly":
                    if (schedule.time == null || !schedule.time.matches("\\d{2}:\\d{2}")) {
                        throw new IllegalArgumentException();
                    }
                    LocalTime.parse(schedule.time);
                    if ("weekly".equals(schedule.kind) && (schedule.weekdays == null || schedule.weekdays.isEmpty()
                            || schedule.weekdays.size() > 7 || schedule.weekdays.stream()
                            .anyMatch(day -> day == null || day < 1 || day > 7))) {
                        throw new IllegalArgumentException();
                    }
                    break;
                default: throw new IllegalArgumentException();
            }
        } catch (DateTimeException | IllegalArgumentException error) {
            throw new IllegalArgumentException("automation.invalidSchedule", error);
        }
    }

    public static Long nextAfter(AutomationModels.Schedule schedule, long after) {
        validate(schedule);
        if ("once".equals(schedule.kind)) return schedule.at > after ? schedule.at : null;
        if ("interval".equals(schedule.kind)) {
            if (schedule.at > after) return schedule.at;
            long period = Duration.ofMinutes(schedule.intervalMinutes).toMillis();
            return Math.addExact(schedule.at, Math.multiplyExact((after - schedule.at) / period + 1, period));
        }
        ZoneId zone = ZoneId.of(schedule.timezone);
        LocalDate date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        LocalTime time = LocalTime.parse(schedule.time);
        Set<Integer> weekdays = "weekly".equals(schedule.kind) ? Set.copyOf(schedule.weekdays) : Set.of();
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate candidateDate = date.plusDays(offset);
            if (!weekdays.isEmpty() && !weekdays.contains(candidateDate.getDayOfWeek().getValue())) continue;
            long candidate = candidateDate.atTime(time).atZone(zone).toInstant().toEpochMilli();
            if (candidate > after) return candidate;
        }
        throw new IllegalArgumentException("automation.invalidSchedule");
    }
}
