package com.sharma.nearlist.domain;

import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether entering a place produces a reminder (spec section 6).
 * Rules are evaluated in order and the first match wins.
 */
public class ReminderDecisionEngine {

    private static final long MINUTE_MS = 60_000L;

    /**
     * @param place               the place that was entered
     * @param open                open tasks of the place's category
     * @param nowMs               current time, epoch milliseconds
     * @param nowMinuteOfDay      current local time, minutes since midnight
     */
    public Decision decide(Place place, List<Task> open, long nowMs, int nowMinuteOfDay) {
        // 1. Nothing to remind about.
        if (open == null || open.isEmpty()) {
            return Decision.suppress(SuppressReason.NO_OPEN_TASKS, null);
        }

        // 2. Place is in cooldown or snoozed.
        if (nowMs < place.quietUntil) {
            String detail = place.lastNotifiedAt > 0
                    ? "notified " + formatElapsed(nowMs - place.lastNotifiedAt) + " ago"
                    : null;
            return Decision.suppress(SuppressReason.COOLDOWN, detail);
        }

        // 3. Only tasks without a window, or whose window contains now.
        List<Task> eligible = new ArrayList<>();
        for (Task task : open) {
            if (!task.hasTimeWindow
                    || TimeWindow.contains(task.windowStartMin, task.windowEndMin, nowMinuteOfDay)) {
                eligible.add(task);
            }
        }
        if (eligible.isEmpty()) {
            Task first = open.get(0);
            return Decision.suppress(SuppressReason.OUTSIDE_TIME_WINDOW,
                    TimeWindow.format(first.windowStartMin, first.windowEndMin));
        }

        // 4. One grouped notification.
        return Decision.notify(eligible);
    }

    /** "1 min", "45 min", "1 h", "3 h". Whole hours from 60 minutes up, rounded down. */
    static String formatElapsed(long elapsedMs) {
        long minutes = Math.max(0, elapsedMs) / MINUTE_MS;
        if (minutes < 1) {
            return "less than 1 min";
        }
        if (minutes < 60) {
            return minutes + " min";
        }
        return (minutes / 60) + " h";
    }
}
