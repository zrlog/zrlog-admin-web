package com.zrlog.admin.business.automation;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Wall-clock schedules use the server timezone; intervals are anchored to their initial timestamp.
 */
public final class TaskSchedule {
    private TaskSchedule() {}

    static AutomationModels.Schedule normalize(AutomationModels.Schedule schedule) {
        if (schedule == null) throw new IllegalArgumentException("automation.invalidSchedule");
        var copy =
                AutomationStore.JSON.fromJson(
                        AutomationStore.JSON.toJson(schedule), AutomationModels.Schedule.class);
        if (copy.atLocal != null && ("once".equals(copy.kind) || "interval".equals(copy.kind))) {
            try {
                var local = LocalDateTime.parse(copy.atLocal);
                var zone = ZoneId.systemDefault();
                // Keep the original seconds and repeated daylight-saving hour when unchanged.
                var previous =
                        copy.at == null
                                ? null
                                : Instant.ofEpochMilli(copy.at).atZone(zone).toLocalDateTime();
                if (previous == null
                        || !local.equals(previous)
                                && !local.equals(previous.truncatedTo(ChronoUnit.MINUTES)))
                    copy.at = local.atZone(zone).toInstant().toEpochMilli();
            } catch (DateTimeException error) {
                throw new IllegalArgumentException("automation.invalidSchedule", error);
            }
        }
        copy.atLocal = null;
        validate(copy);
        return copy;
    }

    static AutomationModels.Schedule forDisplay(AutomationModels.Schedule schedule) {
        if (schedule == null) return null;
        var copy =
                AutomationStore.JSON.fromJson(
                        AutomationStore.JSON.toJson(schedule), AutomationModels.Schedule.class);
        copy.atLocal =
                copy.at == null
                        ? null
                        : Instant.ofEpochMilli(copy.at)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                                .truncatedTo(ChronoUnit.MINUTES)
                                .toString();
        return copy;
    }

    public static void validate(AutomationModels.Schedule schedule) {
        if (schedule == null || schedule.kind == null) {
            throw new IllegalArgumentException("automation.invalidSchedule");
        }
        try {
            switch (schedule.kind) {
                case "once":
                    if (schedule.at == null || schedule.at <= 0)
                        throw new IllegalArgumentException();
                    break;
                case "interval":
                    if (schedule.at == null
                            || schedule.at <= 0
                            || schedule.intervalMinutes == null
                            || schedule.intervalMinutes < 1
                            || schedule.intervalMinutes > 525600) {
                        throw new IllegalArgumentException();
                    }
                    break;
                case "daily":
                case "weekly":
                    if (schedule.time == null || !schedule.time.matches("\\d{2}:\\d{2}")) {
                        throw new IllegalArgumentException();
                    }
                    LocalTime.parse(schedule.time);
                    if ("weekly".equals(schedule.kind)
                            && (schedule.weekdays == null
                                    || schedule.weekdays.isEmpty()
                                    || schedule.weekdays.size() > 7
                                    || schedule.weekdays.stream()
                                            .anyMatch(day -> day == null || day < 1 || day > 7))) {
                        throw new IllegalArgumentException();
                    }
                    break;
                default:
                    throw new IllegalArgumentException();
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
            return Math.addExact(
                    schedule.at, Math.multiplyExact((after - schedule.at) / period + 1, period));
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        LocalTime time = LocalTime.parse(schedule.time);
        Set<Integer> weekdays =
                "weekly".equals(schedule.kind) ? Set.copyOf(schedule.weekdays) : Set.of();
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate candidateDate = date.plusDays(offset);
            if (!weekdays.isEmpty() && !weekdays.contains(candidateDate.getDayOfWeek().getValue()))
                continue;
            long candidate = candidateDate.atTime(time).atZone(zone).toInstant().toEpochMilli();
            if (candidate > after) return candidate;
        }
        throw new IllegalArgumentException("automation.invalidSchedule");
    }
}
