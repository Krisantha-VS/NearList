package com.sharma.nearlist.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReminderDecisionEngineTest {

    private static final long NOW = 1_760_000_000_000L;
    private static final long HOUR = 3_600_000L;
    private static final int MIN_19_00 = 19 * 60;
    private static final int MIN_12_00 = 12 * 60;

    private final ReminderDecisionEngine engine = new ReminderDecisionEngine();

    // U01
    @Test
    public void decide_returnsNoOpenTasks_whenListEmpty() {
        Decision d = engine.decide(place(0, 0), Collections.emptyList(), NOW, MIN_12_00);

        assertFalse(d.isNotify());
        assertEquals(SuppressReason.NO_OPEN_TASKS, d.getReason());
        assertTrue(d.getTasks().isEmpty());
    }

    @Test
    public void decide_returnsNoOpenTasks_beforeCheckingCooldown() {
        Decision d = engine.decide(place(NOW - HOUR, NOW + HOUR), Collections.emptyList(), NOW, MIN_12_00);

        assertEquals(SuppressReason.NO_OPEN_TASKS, d.getReason());
    }

    // U02
    @Test
    public void decide_returnsCooldown_whenNowBeforeQuietUntil() {
        Place place = place(NOW - HOUR, NOW + 3 * HOUR);

        Decision d = engine.decide(place, List.of(task("Milk")), NOW, MIN_12_00);

        assertFalse(d.isNotify());
        assertEquals(SuppressReason.COOLDOWN, d.getReason());
        assertEquals("notified 1 h ago", d.getDetail());
    }

    @Test
    public void decide_cooldownDetailUsesMinutes_whenNotifiedUnderAnHourAgo() {
        Place place = place(NOW - 25 * 60_000L, NOW + HOUR);

        Decision d = engine.decide(place, List.of(task("Milk")), NOW, MIN_12_00);

        assertEquals("notified 25 min ago", d.getDetail());
    }

    @Test
    public void decide_returnsCooldown_beforeCheckingTimeWindows() {
        Place place = place(NOW - HOUR, NOW + HOUR);

        Decision d = engine.decide(place, List.of(windowTask("Milk", 18 * 60, 21 * 60)), NOW, MIN_12_00);

        assertEquals(SuppressReason.COOLDOWN, d.getReason());
    }

    // U03
    @Test
    public void decide_returnsOutsideTimeWindow_whenAllTasksOutsideTheirWindow() {
        List<Task> open = Arrays.asList(
                windowTask("Milk", 18 * 60, 21 * 60),
                windowTask("Bread", 6 * 60, 9 * 60));

        Decision d = engine.decide(place(0, 0), open, NOW, MIN_12_00);

        assertFalse(d.isNotify());
        assertEquals(SuppressReason.OUTSIDE_TIME_WINDOW, d.getReason());
        assertEquals("6–9 PM", d.getDetail());
    }

    // U04
    @Test
    public void decide_notifiesOnlyEligibleTasks_whenWindowsAreMixed() {
        Task inWindow = windowTask("Milk", 18 * 60, 21 * 60);
        Task noWindow = task("Bread");
        Task outside = windowTask("Eggs", 6 * 60, 9 * 60);

        Decision d = engine.decide(place(0, 0), Arrays.asList(inWindow, noWindow, outside), NOW, MIN_19_00);

        assertTrue(d.isNotify());
        assertNull(d.getReason());
        assertEquals(Arrays.asList(inWindow, noWindow), d.getTasks());
    }

    // U05
    @Test
    public void decide_notifies_whenCooldownExpiresExactlyNow() {
        Decision d = engine.decide(place(NOW - 4 * HOUR, NOW), List.of(task("Milk")), NOW, MIN_12_00);

        assertTrue(d.isNotify());
        assertEquals(1, d.getTasks().size());
    }

    @Test
    public void formatElapsed_usesMinutesThenWholeHours() {
        assertEquals("less than 1 min", ReminderDecisionEngine.formatElapsed(30_000L));
        assertEquals("1 min", ReminderDecisionEngine.formatElapsed(60_000L));
        assertEquals("59 min", ReminderDecisionEngine.formatElapsed(59 * 60_000L));
        assertEquals("1 h", ReminderDecisionEngine.formatElapsed(90 * 60_000L));
        assertEquals("3 h", ReminderDecisionEngine.formatElapsed(3 * HOUR));
    }

    private static Place place(long lastNotifiedAt, long quietUntil) {
        Place place = new Place();
        place.id = 1;
        place.name = "Keells Kalutara";
        place.category = Category.GROCERY;
        place.lastNotifiedAt = lastNotifiedAt;
        place.quietUntil = quietUntil;
        return place;
    }

    private static Task task(String title) {
        Task task = new Task();
        task.title = title;
        task.category = Category.GROCERY;
        return task;
    }

    private static Task windowTask(String title, int startMin, int endMin) {
        Task task = task(title);
        task.hasTimeWindow = true;
        task.windowStartMin = startMin;
        task.windowEndMin = endMin;
        return task;
    }
}
