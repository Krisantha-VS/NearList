package com.sharma.nearlist.notify;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.repo.ActivityRepository;
import com.sharma.nearlist.domain.Decision;
import com.sharma.nearlist.domain.ReminderDecisionEngine;
import com.sharma.nearlist.location.GeofenceManager;

import java.util.Calendar;
import java.util.List;

/**
 * The ENTER pipeline of spec section 7: decision engine, then a notification or a suppression,
 * then the Activity log row. Shared by {@code GeofenceBroadcastReceiver} and the debug
 * "Simulate arrival" menu, so both run exactly the same code.
 */
public final class ArrivalHandler {

    private static final String TAG = "Nearlist";

    private static final ReminderDecisionEngine ENGINE = new ReminderDecisionEngine();

    private ArrivalHandler() {
    }

    /**
     * Handles one ENTER for {@code place}. Runs synchronously on the calling (disk) thread, so the
     * log row is stored before it returns.
     *
     * @param simulated true for the debug menu; the log detail gets "(simulated)" appended
     * @return the Activity log row that was written
     */
    @WorkerThread
    public static ActivityEvent handleEnter(Context context, Place place, long now, boolean simulated) {
        AppDatabase db = AppDatabase.getInstance(context);
        List<Task> open = db.taskDao().getOpenByCategorySync(place.category.key());
        Decision decision = ENGINE.decide(place, open, now, minuteOfDay(now));
        String zone = GeofenceManager.requestIdFor(place.id) + " \"" + place.name + "\"";

        ActivityEvent event;
        if (!decision.isNotify()) {
            event = ActivityRepository.enterEvent(place, ActivityEvent.OUTCOME_SUPPRESSED,
                    decision.getReason().name(), markSimulated(context, decision.getDetail(), simulated),
                    open.size(), now);
            Log.i(TAG, "ENTER " + zone + ": SUPPRESSED " + decision.getReason()
                    + (decision.getDetail() != null ? " (" + decision.getDetail() + ")" : "")
                    + ", " + open.size() + " open" + (simulated ? ", simulated" : ""));
        } else {
            List<Task> tasks = decision.getTasks();
            if (NotificationHelper.showPlaceReminder(context, place, tasks)) {
                long cooldownMs = NearlistApp.from(context).getSettingsRepository().getCooldownMs();
                db.placeDao().updateNotifyState(place.id, now, now + cooldownMs);
                event = ActivityRepository.enterEvent(place, ActivityEvent.OUTCOME_NOTIFIED, null,
                        markSimulated(context, null, simulated), tasks.size(), now);
                Log.i(TAG, "ENTER " + zone + ": NOTIFIED " + tasks.size() + " tasks"
                        + (simulated ? ", simulated" : ""));
            } else {
                // Spec section 8: keep the log honest when the user has blocked notifications.
                String detail = context.getString(R.string.log_detail_notifications_blocked);
                event = ActivityRepository.enterEvent(place, ActivityEvent.OUTCOME_SUPPRESSED, null,
                        markSimulated(context, detail, simulated), tasks.size(), now);
                Log.i(TAG, "ENTER " + zone + ": SUPPRESSED, notifications blocked, " + tasks.size()
                        + " tasks" + (simulated ? ", simulated" : ""));
            }
        }
        event.id = db.activityEventDao().insert(event);
        return event;
    }

    @Nullable
    private static String markSimulated(Context context, @Nullable String detail, boolean simulated) {
        if (!simulated) {
            return detail;
        }
        return detail == null
                ? context.getString(R.string.log_detail_simulated)
                : context.getString(R.string.log_detail_with_simulated, detail);
    }

    private static int minuteOfDay(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }
}
