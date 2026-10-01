package com.sharma.nearlist.receiver;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.notify.NotificationHelper;
import com.sharma.nearlist.util.AppExecutors;

/** Handles the "Mark done" / "Mark all done" and "Snooze 1 h" notification actions. */
public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "Nearlist";

    public static final String ACTION_MARK_DONE = "com.sharma.nearlist.action.MARK_DONE";
    public static final String ACTION_SNOOZE = "com.sharma.nearlist.action.SNOOZE";
    public static final String EXTRA_PLACE_ID = "com.sharma.nearlist.extra.PLACE_ID";
    public static final String EXTRA_TASK_IDS = "com.sharma.nearlist.extra.TASK_IDS";

    /** Snooze lets the place alert again on the next visit after one hour. */
    public static final long SNOOZE_MS = 3_600_000L;

    public static PendingIntent markDoneIntent(Context context, long placeId, long[] taskIds) {
        Intent intent = new Intent(context, NotificationActionReceiver.class)
                .setAction(ACTION_MARK_DONE)
                .putExtra(EXTRA_PLACE_ID, placeId)
                .putExtra(EXTRA_TASK_IDS, taskIds);
        return PendingIntent.getBroadcast(context, requestCode(placeId, 0), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static PendingIntent snoozeIntent(Context context, long placeId) {
        Intent intent = new Intent(context, NotificationActionReceiver.class)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_PLACE_ID, placeId);
        return PendingIntent.getBroadcast(context, requestCode(placeId, 1), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Unique per place and action. */
    private static int requestCode(long placeId, int action) {
        return (int) (placeId * 2 + action);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1L);
        if (placeId < 0 || (!ACTION_MARK_DONE.equals(action) && !ACTION_SNOOZE.equals(action))) {
            Log.w(TAG, "Ignoring notification action: " + intent);
            return;
        }
        Context appContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        AppExecutors.diskIO().execute(() -> {
            try {
                // Write directly (not via the async repository) so it completes before finish().
                AppDatabase db = AppDatabase.getInstance(appContext);
                long now = System.currentTimeMillis();
                if (ACTION_MARK_DONE.equals(action)) {
                    long[] taskIds = intent.getLongArrayExtra(EXTRA_TASK_IDS);
                    if (taskIds != null && taskIds.length > 0) {
                        db.taskDao().markDone(taskIds, now);
                    }
                } else {
                    db.placeDao().updateQuietUntil(placeId, now + SNOOZE_MS);
                }
                NotificationHelper.cancel(appContext, placeId);
            } catch (RuntimeException e) {
                Log.e(TAG, "Notification action failed: " + action, e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
