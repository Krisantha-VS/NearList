package com.sharma.nearlist.debug;

import android.content.Context;

import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Consumer;

import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.location.GeofenceManager;
import com.sharma.nearlist.notify.ArrivalHandler;
import com.sharma.nearlist.util.AppExecutors;

import java.util.Calendar;
import java.util.List;

/**
 * Debug-menu data tools (debug builds only; the menu items are hidden in release). Everything
 * runs on {@link AppExecutors#diskIO()}, like every other write, and calls back on the main thread.
 */
public final class DebugData {

    private DebugData() {
    }

    /**
     * Replaces all places, tasks and Activity log rows with the screen 06 sample set, then
     * re-registers the zones. Set the emulator near Kalutara first: adb emu geo fix 79.9600 6.5850.
     */
    public static void loadSampleData(Context context, Runnable onDone) {
        Context appContext = context.getApplicationContext();
        AppExecutors.diskIO().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(appContext);
            db.runInTransaction(() -> {
                deleteAll(db);
                insertSamples(db);
            });
            NotificationManagerCompat.from(appContext).cancelAll();
            // Initial trigger 0 for all zones: being inside a sample place does not fire a reminder.
            GeofenceManager.syncAllBlocking(appContext, GeofenceManager.NO_CHANGED_PLACE);
            AppExecutors.main(onDone);
        });
    }

    /**
     * Deletes all places, tasks and Activity log rows, cancels shown reminders, removes every zone
     * and resets the registered-zone count (the sync with no places does both).
     */
    public static void clearAll(Context context, Runnable onDone) {
        Context appContext = context.getApplicationContext();
        AppExecutors.diskIO().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(appContext);
            db.runInTransaction(() -> deleteAll(db));
            NotificationManagerCompat.from(appContext).cancelAll();
            GeofenceManager.syncAllBlocking(appContext, GeofenceManager.NO_CHANGED_PLACE);
            AppExecutors.main(onDone);
        });
    }

    public static void loadPlaces(Context context, Consumer<List<Place>> onLoaded) {
        Context appContext = context.getApplicationContext();
        AppExecutors.diskIO().execute(() -> {
            List<Place> places = AppDatabase.getInstance(appContext).placeDao().getAllSync();
            AppExecutors.main(() -> onLoaded.accept(places));
        });
    }

    /**
     * Runs the real ENTER pipeline for {@code placeId}, skipping only Play services.
     * Calls back with the logged row, or null if the place no longer exists.
     */
    public static void simulateArrival(Context context, long placeId, Consumer<ActivityEvent> onDone) {
        Context appContext = context.getApplicationContext();
        AppExecutors.diskIO().execute(() -> {
            Place place = AppDatabase.getInstance(appContext).placeDao().getByIdSync(placeId);
            ActivityEvent event = place == null ? null
                    : ArrivalHandler.handleEnter(appContext, place, System.currentTimeMillis(), true);
            AppExecutors.main(() -> onDone.accept(event));
        });
    }

    @WorkerThread
    private static void deleteAll(AppDatabase db) {
        db.activityEventDao().clear();
        db.taskDao().deleteAll();
        db.placeDao().deleteAll();
    }

    @WorkerThread
    private static void insertSamples(AppDatabase db) {
        long now = System.currentTimeMillis();
        db.placeDao().insert(samplePlace("Keells Kalutara", Category.GROCERY, 6.5854, 79.9607, 300, now));
        db.placeDao().insert(samplePlace("Ceypetco Kalutara North", Category.FUEL, 6.5979, 79.9550, 250, now));
        db.placeDao().insert(samplePlace("Osu Sala Panadura", Category.PHARMACY, 6.7110, 79.9070, 150, now));

        db.taskDao().insert(sampleTask("Buy milk and eggs", Category.GROCERY, 18 * 60, 21 * 60, now - 4_000));
        db.taskDao().insert(sampleTask("Refill petrol", Category.FUEL, -1, -1, now - 3_000));
        db.taskDao().insert(sampleTask("Pick up prescription", Category.PHARMACY, 8 * 60, 20 * 60, now - 2_000));
        db.taskDao().insert(sampleTask("Return library book", Category.OTHER, -1, -1, now - 1_000));

        Task bread = sampleTask("Buy bread", Category.GROCERY, -1, -1, now - 5_000);
        bread.done = true;
        bread.completedAt = latestPast942(now);
        db.taskDao().insert(bread);
    }

    /** 9:42 today, or yesterday if that is still ahead, so the sample is never completed in the future. */
    private static long latestPast942(long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 42);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() > now) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }
        return calendar.getTimeInMillis();
    }

    private static Place samplePlace(String name, Category category, double lat, double lng, int radiusM,
                                     long createdAt) {
        Place place = new Place();
        place.name = name;
        place.category = category;
        place.latitude = lat;
        place.longitude = lng;
        place.radiusM = radiusM;
        place.createdAt = createdAt;
        return place;
    }

    /** @param startMin window start in minutes, or -1 for no window */
    private static Task sampleTask(String title, Category category, int startMin, int endMin, long createdAt) {
        Task task = new Task();
        task.title = title;
        task.category = category;
        task.hasTimeWindow = startMin >= 0;
        task.windowStartMin = Math.max(startMin, 0);
        task.windowEndMin = Math.max(endMin, 0);
        task.createdAt = createdAt;
        return task;
    }
}
