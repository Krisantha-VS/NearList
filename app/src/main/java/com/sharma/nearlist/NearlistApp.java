package com.sharma.nearlist;

import android.app.Application;
import android.content.Context;

import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.repo.ActivityRepository;
import com.sharma.nearlist.data.repo.PlaceRepository;
import com.sharma.nearlist.data.repo.SettingsRepository;
import com.sharma.nearlist.data.repo.TaskRepository;
import com.sharma.nearlist.notify.NotificationHelper;

/** Holds the app-wide singletons. Reach it from any Context with {@link #from(Context)}. */
public class NearlistApp extends Application {

    private PlaceRepository placeRepository;
    private TaskRepository taskRepository;
    private ActivityRepository activityRepository;
    private SettingsRepository settingsRepository;

    public static NearlistApp from(Context context) {
        return (NearlistApp) context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        // Building the database is cheap: Room opens the file on the first query, off the main thread.
        AppDatabase db = AppDatabase.getInstance(this);
        placeRepository = new PlaceRepository(this, db.placeDao());
        taskRepository = new TaskRepository(db.taskDao());
        activityRepository = new ActivityRepository(db.activityEventDao());
        settingsRepository = SettingsRepository.create(this);
    }

    public PlaceRepository getPlaceRepository() {
        return placeRepository;
    }

    public TaskRepository getTaskRepository() {
        return taskRepository;
    }

    public ActivityRepository getActivityRepository() {
        return activityRepository;
    }

    public SettingsRepository getSettingsRepository() {
        return settingsRepository;
    }
}
