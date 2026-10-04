package com.sharma.nearlist.ui.tasks;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import androidx.annotation.NonNull;
import androidx.core.location.LocationManagerCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.data.repo.PlaceRepository;
import com.sharma.nearlist.data.repo.SettingsRepository;
import com.sharma.nearlist.data.repo.TaskRepository;
import com.sharma.nearlist.domain.TaskListItem;
import com.sharma.nearlist.domain.TaskSorter;
import com.sharma.nearlist.notify.NotificationHelper;
import com.sharma.nearlist.util.PermissionHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Home list state: open tasks sorted by {@link TaskSorter}, the completed section, the category
 * filter and the status banner.
 */
public class TasksViewModel extends AndroidViewModel {

    /** Home banner, in priority order. */
    public enum Banner { NONE, LOCATION_OFF, BACKGROUND_OFF, NOTIFICATIONS_OFF }

    /** Everything the list area needs, published together so the screen never shows a mix. */
    public static final class UiState {
        public final List<TaskAdapter.Row> rows;
        public final int openCount;
        public final boolean noTasksAtAll;
        public final boolean locationKnown;

        UiState(List<TaskAdapter.Row> rows, int openCount, boolean noTasksAtAll, boolean locationKnown) {
            this.rows = rows;
            this.openCount = openCount;
            this.noTasksAtAll = noTasksAtAll;
            this.locationKnown = locationKnown;
        }
    }

    private final TaskRepository taskRepository;
    private final PlaceRepository placeRepository;
    private final SettingsRepository settings;

    private final LiveData<List<Task>> openTasks;
    private final LiveData<List<Task>> completedTasks;
    private final LiveData<List<Place>> places;
    private final MutableLiveData<Location> location = new MutableLiveData<>();
    private final MutableLiveData<Category> filter = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> completedExpanded = new MutableLiveData<>(true);
    private final MediatorLiveData<UiState> state = new MediatorLiveData<>();
    private final MutableLiveData<Banner> banner = new MutableLiveData<>(Banner.NONE);

    public TasksViewModel(@NonNull Application application) {
        super(application);
        NearlistApp app = NearlistApp.from(application);
        taskRepository = app.getTaskRepository();
        placeRepository = app.getPlaceRepository();
        settings = app.getSettingsRepository();

        openTasks = taskRepository.observeOpen();
        completedTasks = taskRepository.observeCompleted();
        places = placeRepository.observeAll();
        state.addSource(openTasks, v -> recompute());
        state.addSource(completedTasks, v -> recompute());
        state.addSource(places, v -> recompute());
        state.addSource(location, v -> recompute());
        state.addSource(filter, v -> recompute());
        state.addSource(completedExpanded, v -> recompute());
    }

    public LiveData<UiState> getState() {
        return state;
    }

    public LiveData<Banner> getBanner() {
        return banner;
    }

    public LiveData<Category> getFilter() {
        return filter;
    }

    public void setLocation(Location current) {
        location.setValue(current);
    }

    /** @param category the category to show, or null for all */
    public void setFilter(Category category) {
        filter.setValue(category);
    }

    public void toggleCompletedExpanded() {
        Boolean expanded = completedExpanded.getValue();
        completedExpanded.setValue(expanded == null || !expanded);
    }

    public void setDone(Task task, boolean done) {
        if (done) {
            taskRepository.markDone(new long[]{task.id});
        } else {
            taskRepository.markOpen(task.id);
        }
    }

    /** Re-evaluates the banner; call from onResume (permissions and location may have changed). */
    public void refreshBanner() {
        banner.setValue(settings.isBannerDismissedSession() ? Banner.NONE : computeBanner(getApplication()));
    }

    /** Hides the banner until the app restarts. */
    public void dismissBanner() {
        settings.setBannerDismissedSession(true);
        banner.setValue(Banner.NONE);
    }

    private static Banner computeBanner(Context context) {
        LocationManager lm = context.getSystemService(LocationManager.class);
        if (lm != null && !LocationManagerCompat.isLocationEnabled(lm)) {
            return Banner.LOCATION_OFF;
        }
        // On API 29+ geofencing needs "Allow all the time"; below that, foreground location.
        if (!PermissionHelper.hasBackgroundLocation(context)) {
            return Banner.BACKGROUND_OFF;
        }
        if (!NotificationHelper.canPostNotifications(context)) {
            return Banner.NOTIFICATIONS_OFF;
        }
        return Banner.NONE;
    }

    private void recompute() {
        List<Task> open = openTasks.getValue();
        List<Task> completed = completedTasks.getValue();
        List<Place> allPlaces = places.getValue();
        if (open == null || completed == null || allPlaces == null) {
            return; // Wait until Room has delivered all three.
        }
        Category category = filter.getValue();
        Location current = location.getValue();
        Double lat = current != null ? current.getLatitude() : null;
        Double lng = current != null ? current.getLongitude() : null;

        List<TaskListItem> items = TaskSorter.build(open, allPlaces, lat, lng, category);
        List<TaskAdapter.Row> rows = new ArrayList<>();
        boolean nearestMarked = false;
        for (TaskListItem item : items) {
            boolean nearest = !nearestMarked && item.distanceMeters != null;
            nearestMarked |= nearest;
            rows.add(TaskAdapter.Row.open(item, nearest));
        }

        List<Task> done = new ArrayList<>();
        for (Task task : completed) {
            if (category == null || task.category == category) {
                done.add(task);
            }
        }
        if (!done.isEmpty()) {
            boolean expanded = Boolean.TRUE.equals(completedExpanded.getValue());
            rows.add(TaskAdapter.Row.header(done.size(), expanded));
            if (expanded) {
                for (Task task : done) {
                    rows.add(TaskAdapter.Row.completed(task));
                }
            }
        }

        boolean noTasksAtAll = open.isEmpty() && completed.isEmpty();
        state.setValue(new UiState(Collections.unmodifiableList(rows), items.size(), noTasksAtAll, current != null));
    }
}
