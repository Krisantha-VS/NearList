package com.sharma.nearlist.ui.activity;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.repo.ActivityRepository;
import com.sharma.nearlist.domain.ActivityCsv;
import com.sharma.nearlist.util.AppExecutors;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/** Activity log state (screens 21–23): outcome filter and events grouped by local day. */
public class ActivityViewModel extends AndroidViewModel {

    private final ActivityRepository repository;
    /** Null for all, otherwise an {@code ActivityEvent.OUTCOME_} constant. */
    private final MutableLiveData<String> filter = new MutableLiveData<>(null);
    private final LiveData<List<ActivityEvent>> events;
    private final LiveData<Integer> totalCount;
    private final MediatorLiveData<List<ActivityAdapter.Row>> rows = new MediatorLiveData<>();

    public ActivityViewModel(@NonNull Application application) {
        super(application);
        repository = NearlistApp.from(application).getActivityRepository();
        events = Transformations.switchMap(filter,
                outcome -> outcome == null ? repository.observeAll() : repository.observeByOutcome(outcome));
        totalCount = repository.observeCount();
        rows.addSource(events, list -> rows.setValue(group(list, filter.getValue())));
    }

    public LiveData<List<ActivityAdapter.Row>> getRows() {
        return rows;
    }

    /** Events in the whole log, regardless of the filter (empty state, overflow menu). */
    public LiveData<Integer> getTotalCount() {
        return totalCount;
    }

    public LiveData<String> getFilter() {
        return filter;
    }

    /** @param outcome null for all, or {@code OUTCOME_NOTIFIED} / {@code OUTCOME_SUPPRESSED} */
    public void setFilter(@Nullable String outcome) {
        String current = filter.getValue();
        if (current == null ? outcome != null : !current.equals(outcome)) {
            filter.setValue(outcome);
        }
    }

    public void clear() {
        repository.clear();
    }

    /** Builds the CSV of the whole log on the disk thread and delivers it on the main thread. */
    public void exportCsv(Consumer<String> onReady) {
        AppExecutors.diskIO().execute(() -> {
            String csv = ActivityCsv.build(repository.getAllSync(), TimeZone.getDefault());
            AppExecutors.main(() -> onReady.accept(csv));
        });
    }

    /** Newest first, a header before each local day, with first/last flags for the timeline. */
    static List<ActivityAdapter.Row> group(List<ActivityEvent> events, @Nullable String outcome) {
        List<ActivityAdapter.Row> result = new ArrayList<>();
        if (events == null) {
            return result;
        }
        Calendar calendar = Calendar.getInstance();
        int i = 0;
        while (i < events.size()) {
            long dayStart = startOfDay(calendar, events.get(i).timestamp);
            int end = i;
            while (end < events.size() && startOfDay(calendar, events.get(end).timestamp) == dayStart) {
                end++;
            }
            result.add(ActivityAdapter.Row.day(dayStart, end - i, outcome));
            for (int j = i; j < end; j++) {
                result.add(ActivityAdapter.Row.event(events.get(j), j == i, j == end - 1));
            }
            i = end;
        }
        return Collections.unmodifiableList(result);
    }

    private static long startOfDay(Calendar calendar, long timeMs) {
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
