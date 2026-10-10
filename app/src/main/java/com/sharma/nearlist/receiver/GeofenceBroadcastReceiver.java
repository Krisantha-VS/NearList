package com.sharma.nearlist.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.dao.ActivityEventDao;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.repo.ActivityRepository;
import com.sharma.nearlist.location.GeofenceManager;
import com.sharma.nearlist.notify.ArrivalHandler;
import com.sharma.nearlist.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * Receives geofence transitions from Play services and turns them into reminders and Activity
 * log entries (spec section 7, sequence diagram). All database work is synchronous on the disk
 * thread inside goAsync(), so every row is written before finish().
 */
public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "Nearlist";

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        logReceived(intent, event);
        if (event == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        AppExecutors.diskIO().execute(() -> {
            try {
                handle(appContext, event);
            } catch (RuntimeException e) {
                Log.e(TAG, "Geofence event handling failed", e);
            } finally {
                pendingResult.finish();
            }
        });
    }

    private static void logReceived(Intent intent, GeofencingEvent event) {
        if (event == null) {
            Log.i(TAG, "onReceive action=" + intent.getAction() + ": no geofencing data");
            return;
        }
        if (event.hasError()) {
            Log.i(TAG, "onReceive action=" + intent.getAction() + " hasError=true code=" + event.getErrorCode());
            return;
        }
        List<Geofence> geofences = event.getTriggeringGeofences();
        List<String> ids = new ArrayList<>();
        if (geofences != null) {
            for (Geofence geofence : geofences) {
                ids.add(geofence.getRequestId());
            }
        }
        Log.i(TAG, "onReceive action=" + intent.getAction() + " hasError=false transition="
                + transitionName(event.getGeofenceTransition()) + " geofences=" + ids.size() + " ids=" + ids);
    }

    private static void handle(Context context, GeofencingEvent event) {
        AppDatabase db = AppDatabase.getInstance(context);
        ActivityEventDao log = db.activityEventDao();
        long now = System.currentTimeMillis();

        if (event.hasError()) {
            int code = event.getErrorCode();
            String detail = context.getString(R.string.log_geofence_error, code,
                    GeofenceStatusCodes.getStatusCodeString(code));
            log.insert(ActivityRepository.systemEvent(ActivityEvent.SYSTEM_LOCATION_ERROR, detail, now));
            Log.w(TAG, detail);
            return;
        }

        int transition = event.getGeofenceTransition();
        List<Geofence> geofences = event.getTriggeringGeofences();
        if (geofences == null) {
            return;
        }
        for (Geofence geofence : geofences) {
            long placeId = GeofenceManager.placeIdFromRequestId(geofence.getRequestId());
            Place place = placeId > 0 ? db.placeDao().getByIdSync(placeId) : null;
            if (place == null) {
                // Deleted since registration; the next sync removes its zone.
                Log.i(TAG, "Ignoring transition for unknown zone " + geofence.getRequestId());
                continue;
            }
            if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                log.insert(ActivityRepository.exitEvent(place, now));
                Log.i(TAG, "EXIT " + geofence.getRequestId() + " \"" + place.name + "\": LOGGED");
            } else if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                ArrivalHandler.handleEnter(context, place, now, false);
            } else {
                Log.i(TAG, "Ignoring transition " + transition + " for " + geofence.getRequestId());
            }
        }
    }

    private static String transitionName(int transition) {
        switch (transition) {
            case Geofence.GEOFENCE_TRANSITION_ENTER:
                return "ENTER";
            case Geofence.GEOFENCE_TRANSITION_EXIT:
                return "EXIT";
            case Geofence.GEOFENCE_TRANSITION_DWELL:
                return "DWELL";
            default:
                return String.valueOf(transition);
        }
    }
}
