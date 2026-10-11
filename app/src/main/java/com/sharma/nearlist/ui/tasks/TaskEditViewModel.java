package com.sharma.nearlist.ui.tasks;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.data.repo.PlaceRepository;
import com.sharma.nearlist.data.repo.TaskRepository;
import com.sharma.nearlist.util.AppExecutors;

/**
 * Form state for {@link AddEditTaskActivity} (screens 08–10). Lives across rotation, so typed
 * input, the chosen category and the time window survive a configuration change.
 */
public class TaskEditViewModel extends AndroidViewModel {

    /** Where the form is: waiting for the task to load, ready to edit, or the task is gone. */
    public enum LoadState { LOADING, READY, NOT_FOUND }

    static final int DEFAULT_WINDOW_START_MIN = 18 * 60;
    static final int DEFAULT_WINDOW_END_MIN = 21 * 60;
    private static final Category DEFAULT_CATEGORY = Category.GROCERY;

    private final TaskRepository taskRepository;

    private final MutableLiveData<LoadState> loadState = new MutableLiveData<>(LoadState.LOADING);
    private final MutableLiveData<String> title = new MutableLiveData<>("");
    private final MutableLiveData<Category> category = new MutableLiveData<>(DEFAULT_CATEGORY);
    private final MutableLiveData<Boolean> windowEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> windowStartMin = new MutableLiveData<>(DEFAULT_WINDOW_START_MIN);
    private final MutableLiveData<Integer> windowEndMin = new MutableLiveData<>(DEFAULT_WINDOW_END_MIN);
    private final LiveData<Integer> placeCount;

    private boolean started;
    private long taskId = AddEditTaskActivity.NEW_TASK;
    /** The stored task in edit mode, so fields the form doesn't show (done, createdAt) are kept. */
    private Task original;
    private String notes = "";

    public TaskEditViewModel(@NonNull Application application) {
        super(application);
        NearlistApp app = NearlistApp.from(application);
        taskRepository = app.getTaskRepository();
        PlaceRepository placeRepository = app.getPlaceRepository();
        placeCount = Transformations.switchMap(category, placeRepository::observeCountByCategory);
    }

    /** Loads the task once; later calls (after rotation) keep the current form state. */
    public void start(long id) {
        if (started) {
            return;
        }
        started = true;
        taskId = id;
        if (id == AddEditTaskActivity.NEW_TASK) {
            loadState.setValue(LoadState.READY);
            return;
        }
        AppDatabase db = AppDatabase.getInstance(getApplication());
        AppExecutors.diskIO().execute(() -> {
            Task task = db.taskDao().getByIdSync(id);
            AppExecutors.main(() -> applyLoaded(task));
        });
    }

    private void applyLoaded(Task task) {
        if (task == null) {
            loadState.setValue(LoadState.NOT_FOUND);
            return;
        }
        original = task;
        title.setValue(task.title);
        category.setValue(task.category);
        windowEnabled.setValue(task.hasTimeWindow);
        if (task.hasTimeWindow) {
            windowStartMin.setValue(task.windowStartMin);
            windowEndMin.setValue(task.windowEndMin);
        }
        notes = task.notes != null ? task.notes : "";
        loadState.setValue(LoadState.READY);
    }

    public boolean isEditing() {
        return taskId != AddEditTaskActivity.NEW_TASK;
    }

    public LiveData<LoadState> getLoadState() {
        return loadState;
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<Category> getCategory() {
        return category;
    }

    public LiveData<Boolean> getWindowEnabled() {
        return windowEnabled;
    }

    public LiveData<Integer> getWindowStartMin() {
        return windowStartMin;
    }

    public LiveData<Integer> getWindowEndMin() {
        return windowEndMin;
    }

    /** Saved places of the selected category, live from Room. */
    public LiveData<Integer> getPlaceCount() {
        return placeCount;
    }

    public String getNotes() {
        return notes;
    }

    public void setTitle(String value) {
        if (!value.equals(title.getValue())) {
            title.setValue(value);
        }
    }

    public void setCategory(Category value) {
        if (value != category.getValue()) {
            category.setValue(value);
        }
    }

    public void setWindowEnabled(boolean enabled) {
        if (enabled != Boolean.TRUE.equals(windowEnabled.getValue())) {
            windowEnabled.setValue(enabled);
        }
    }

    public void setWindowStartMin(int minutes) {
        windowStartMin.setValue(minutes);
    }

    public void setWindowEndMin(int minutes) {
        windowEndMin.setValue(minutes);
    }

    public void setNotes(String value) {
        notes = value;
    }

    public boolean canSave() {
        String current = title.getValue();
        return loadState.getValue() == LoadState.READY && current != null && !current.trim().isEmpty();
    }

    /** Inserts or updates the task on the disk thread. Call {@link #canSave()} first. */
    public void save() {
        Task task = original != null ? original : new Task();
        task.title = trimmed(title.getValue());
        task.category = category.getValue() != null ? category.getValue() : DEFAULT_CATEGORY;
        task.hasTimeWindow = Boolean.TRUE.equals(windowEnabled.getValue());
        task.windowStartMin = task.hasTimeWindow ? valueOr(windowStartMin, DEFAULT_WINDOW_START_MIN) : 0;
        task.windowEndMin = task.hasTimeWindow ? valueOr(windowEndMin, DEFAULT_WINDOW_END_MIN) : 0;
        String trimmedNotes = notes.trim();
        task.notes = trimmedNotes.isEmpty() ? null : trimmedNotes;
        if (isEditing()) {
            taskRepository.update(task);
        } else {
            taskRepository.insert(task);
        }
    }

    /** Edit mode only. */
    public void delete() {
        if (original != null) {
            taskRepository.delete(original);
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static int valueOr(LiveData<Integer> data, int fallback) {
        Integer value = data.getValue();
        return value != null ? value : fallback;
    }
}
