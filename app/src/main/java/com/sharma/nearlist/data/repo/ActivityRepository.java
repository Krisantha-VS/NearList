package com.sharma.nearlist.data.repo;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.sharma.nearlist.data.dao.ActivityEventDao;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.util.AppExecutors;

import java.util.List;

public class ActivityRepository {

    private final ActivityEventDao dao;

    public ActivityRepository(ActivityEventDao dao) {
        this.dao = dao;
    }

    public LiveData<List<ActivityEvent>> observeAll() {
        return dao.observeAll();
    }

    /** @param outcome one of the {@code ActivityEvent.OUTCOME_} constants */
    public LiveData<List<ActivityEvent>> observeByOutcome(String outcome) {
        return dao.observeByOutcome(outcome);
    }

    /**
     * Logs an ENTER transition.
     *
     * @param outcome NOTIFIED or SUPPRESSED
     * @param reason  SuppressReason name when suppressed, otherwise null
     */
    public void logEnter(Place place, String outcome, @Nullable String reason,
                         @Nullable String detail, int taskCount) {
        insert(enterEvent(place, outcome, reason, detail, taskCount, System.currentTimeMillis()));
    }

    /** Logs an EXIT transition: always LOGGED, the engine is not consulted. */
    public void logExit(Place place) {
        insert(exitEvent(place, System.currentTimeMillis()));
    }

    /**
     * Logs a SYSTEM / INFO event such as re-registration or a geofence error.
     *
     * @param kind one of the {@code ActivityEvent.SYSTEM_} constants
     */
    public void logSystem(String kind, String detail) {
        insert(systemEvent(kind, detail, System.currentTimeMillis()));
    }

    public LiveData<Integer> observeCount() {
        return dao.observeCount();
    }

    /** All events, newest first, on the calling (disk) thread; for the CSV export. */
    public List<ActivityEvent> getAllSync() {
        return dao.getAllSync();
    }

    public void clear() {
        AppExecutors.diskIO().execute(dao::clear);
    }

    private void insert(ActivityEvent event) {
        AppExecutors.diskIO().execute(() -> dao.insert(event));
    }

    public static ActivityEvent enterEvent(Place place, String outcome, @Nullable String reason,
                                           @Nullable String detail, int taskCount, long timestamp) {
        ActivityEvent event = placeEvent(place, ActivityEvent.TYPE_ENTER, timestamp);
        event.outcome = outcome;
        event.reason = reason;
        event.detail = detail;
        event.taskCount = taskCount;
        return event;
    }

    public static ActivityEvent exitEvent(Place place, long timestamp) {
        ActivityEvent event = placeEvent(place, ActivityEvent.TYPE_EXIT, timestamp);
        event.outcome = ActivityEvent.OUTCOME_LOGGED;
        return event;
    }

    /** @param kind one of the {@code ActivityEvent.SYSTEM_} constants */
    public static ActivityEvent systemEvent(String kind, String detail, long timestamp) {
        ActivityEvent event = new ActivityEvent();
        event.timestamp = timestamp;
        event.type = ActivityEvent.TYPE_SYSTEM;
        event.outcome = ActivityEvent.OUTCOME_INFO;
        event.reason = kind;
        event.detail = detail;
        return event;
    }

    private static ActivityEvent placeEvent(Place place, String type, long timestamp) {
        ActivityEvent event = new ActivityEvent();
        event.timestamp = timestamp;
        event.type = type;
        event.placeId = place.id;
        event.placeName = place.name;
        event.category = place.category;
        return event;
    }
}
