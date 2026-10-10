package com.sharma.nearlist.location;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;
import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.repo.ActivityRepository;
import com.sharma.nearlist.data.repo.SettingsRepository;
import com.sharma.nearlist.receiver.GeofenceBroadcastReceiver;
import com.sharma.nearlist.util.AppExecutors;
import com.sharma.nearlist.util.PermissionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Keeps registered geofences in line with the places table (spec section 7).
 *
 * <p>Static methods rather than a singleton: the class holds no state of its own. The registered
 * zones live in Play services, the counts in {@link SettingsRepository}, and the PendingIntent is
 * rebuilt identically on every call. Receivers, repositories and activities can all call it with
 * just a Context.
 *
 * <p>Every sync runs on the single {@link AppExecutors#diskIO()} thread and waits for each Play
 * services task before the next step, so concurrent sync requests are serialised and a remove
 * from one sync can never interleave with an add from another.
 */
public final class GeofenceManager {

    private static final String TAG = "Nearlist";
    private static final String REQUEST_ID_PREFIX = "place-";

    /** Pass as {@code changedPlaceId} when no place should get an initial ENTER trigger. */
    public static final long NO_CHANGED_PLACE = 0L;

    static final long RESPONSIVENESS_DEMO_MS = 0L;
    static final long RESPONSIVENESS_LOW_BATTERY_MS = 300_000L;
    static final long RESPONSIVENESS_DEFAULT_MS = 60_000L;
    static final int LOW_BATTERY_PERCENT = 20;

    /** How long a successful sync counts as fresh for {@link #syncAllOnAppOpen(Context)}. */
    static final long RECENT_SYNC_MS = 300_000L;

    private static final long PLAY_SERVICES_TIMEOUT_S = 30L;
    private static final int NO_STATUS_CODE = Integer.MIN_VALUE;

    private GeofenceManager() {
    }

    /**
     * Re-registers every place with initial trigger 0, so a re-sync never re-fires ENTER for a
     * place the user is already in. Used on app open, boot, settings changes and place deletes.
     */
    public static void syncAll(Context context) {
        syncAll(context, NO_CHANGED_PLACE);
    }

    /**
     * Like {@link #syncAll(Context)}, but registers {@code changedPlaceId} in its own request with
     * INITIAL_TRIGGER_ENTER, so saving a place while standing in it fires immediately.
     * Used after a place insert or edit.
     */
    public static void syncAll(Context context, long changedPlaceId) {
        Context appContext = context.getApplicationContext();
        AppExecutors.diskIO().execute(() -> syncAllBlocking(appContext, changedPlaceId));
    }

    /**
     * App-open self-healing (MainActivity.onStart). Like {@link #syncAll(Context)}, but skipped
     * when, at the time it runs, a sync succeeded less than {@link #RECENT_SYNC_MS} ago and still
     * covers every place. A place saved moments ago was registered with INITIAL_TRIGGER_ENTER;
     * re-registering it straight away with trigger 0 could drop that pending ENTER.
     */
    public static void syncAllOnAppOpen(Context context) {
        Context appContext = context.getApplicationContext();
        AppExecutors.diskIO().execute(() -> {
            SettingsRepository settings = NearlistApp.from(appContext).getSettingsRepository();
            int places = AppDatabase.getInstance(appContext).placeDao().countSync();
            if (isRecentSync(settings.getZonesRegisteredAt(), settings.getZonesRegistered(), places,
                    System.currentTimeMillis())) {
                Log.i(TAG, "syncAll on app open skipped: " + places + " zones registered recently");
                return;
            }
            syncAllBlocking(appContext, NO_CHANGED_PLACE);
        });
    }

    /** True when the last successful sync is younger than {@link #RECENT_SYNC_MS} and matches the place count. */
    static boolean isRecentSync(long registeredAt, int zonesRegistered, int placeCount, long now) {
        return registeredAt > 0 && now - registeredAt < RECENT_SYNC_MS && zonesRegistered == placeCount;
    }

    /**
     * Performs the sync on the calling thread and returns the number of zones now registered.
     * Must run on {@link AppExecutors#diskIO()}, e.g. from a receiver that needs the count.
     */
    @WorkerThread
    @SuppressLint("MissingPermission")
    public static int syncAllBlocking(Context context, long changedPlaceId) {
        Context appContext = context.getApplicationContext();
        SettingsRepository settings = NearlistApp.from(appContext).getSettingsRepository();

        if (!PermissionHelper.hasFineLocation(appContext)) {
            Log.i(TAG, "syncAll: no fine location permission, 0 zones registered");
            settings.setZonesRegistered(0, 0L);
            return 0;
        }

        GeofencingClient client = LocationServices.getGeofencingClient(appContext);
        PendingIntent pendingIntent = getPendingIntent(appContext);
        List<Place> places = AppDatabase.getInstance(appContext).placeDao().getAllSync();

        try {
            try {
                await(client.removeGeofences(pendingIntent));
            } catch (ExecutionException e) {
                // Adding with the same request IDs replaces old zones, so carry on.
                Log.w(TAG, "removeGeofences failed: " + describe(e), e);
            }

            if (places.isEmpty()) {
                settings.setZonesRegistered(0, System.currentTimeMillis());
                Log.i(TAG, "syncAll: no places, 0 zones registered");
                return 0;
            }

            long responsivenessMs = computeResponsivenessMs(appContext);
            List<Geofence> others = new ArrayList<>();
            Geofence changed = null;
            for (Place place : places) {
                Geofence geofence = buildGeofence(place, responsivenessMs);
                if (place.id == changedPlaceId) {
                    changed = geofence;
                } else {
                    others.add(geofence);
                }
            }

            if (!others.isEmpty()) {
                await(client.addGeofences(buildRequest(others, 0), pendingIntent));
            }
            if (changed != null) {
                List<Geofence> single = new ArrayList<>();
                single.add(changed);
                await(client.addGeofences(
                        buildRequest(single, GeofencingRequest.INITIAL_TRIGGER_ENTER), pendingIntent));
            }

            settings.setZonesRegistered(places.size(), System.currentTimeMillis());
            Log.i(TAG, "syncAll: addGeofences succeeded, " + places.size() + " zones, responsiveness "
                    + responsivenessMs + " ms");
            return places.size();
        } catch (ExecutionException e) {
            Log.e(TAG, "addGeofences failed: " + describe(e), e);
            if (statusCode(e) == GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION) {
                // API 29+ without "Allow all the time": no zones at all, even while the app is open.
                logSystemNow(appContext, appContext.getString(R.string.log_sync_needs_background_location));
            } else {
                logSyncFailure(appContext, describe(e));
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Geofencing permission missing", e);
            logSyncFailure(appContext, e.getClass().getSimpleName());
        } catch (TimeoutException e) {
            Log.e(TAG, "Play services did not answer within " + PLAY_SERVICES_TIMEOUT_S + " s", e);
            logSyncFailure(appContext, e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "syncAll interrupted", e);
        }
        settings.setZonesRegistered(0, 0L);
        return 0;
    }

    /** Single broadcast PendingIntent to {@link GeofenceBroadcastReceiver}; mutable so Play services can add the event. */
    public static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /** Notification responsiveness for the current demo, battery-aware and battery state. */
    public static long computeResponsivenessMs(Context context) {
        SettingsRepository settings = NearlistApp.from(context).getSettingsRepository();
        return responsivenessMs(settings.isDemoActive(), settings.isBatteryAware(),
                BatteryHelper.getLevelPercent(context), BatteryHelper.isCharging(context));
    }

    /** Spec section 7 table. An unknown battery level (-1) counts as not low. */
    static long responsivenessMs(boolean demoActive, boolean batteryAware, int batteryPercent, boolean charging) {
        if (demoActive) {
            return RESPONSIVENESS_DEMO_MS;
        }
        if (batteryAware && batteryPercent >= 0 && batteryPercent < LOW_BATTERY_PERCENT && !charging) {
            return RESPONSIVENESS_LOW_BATTERY_MS;
        }
        return RESPONSIVENESS_DEFAULT_MS;
    }

    public static String requestIdFor(long placeId) {
        return REQUEST_ID_PREFIX + placeId;
    }

    /** Parses "place-&lt;id&gt;". Returns -1 for any other format. */
    public static long placeIdFromRequestId(String requestId) {
        if (requestId == null || !requestId.startsWith(REQUEST_ID_PREFIX)) {
            return -1L;
        }
        try {
            return Long.parseLong(requestId.substring(REQUEST_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static Geofence buildGeofence(Place place, long responsivenessMs) {
        return new Geofence.Builder()
                .setRequestId(requestIdFor(place.id))
                .setCircularRegion(place.latitude, place.longitude, place.radiusM)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .setNotificationResponsiveness((int) responsivenessMs)
                .build();
    }

    private static GeofencingRequest buildRequest(List<Geofence> geofences, int initialTrigger) {
        return new GeofencingRequest.Builder()
                .setInitialTrigger(initialTrigger)
                .addGeofences(geofences)
                .build();
    }

    private static <T> T await(com.google.android.gms.tasks.Task<T> task)
            throws ExecutionException, InterruptedException, TimeoutException {
        return Tasks.await(task, PLAY_SERVICES_TIMEOUT_S, TimeUnit.SECONDS);
    }

    /** "1000 GEOFENCE_NOT_AVAILABLE" for Play services errors, otherwise the exception type. */
    private static String describe(ExecutionException e) {
        int code = statusCode(e);
        if (code != NO_STATUS_CODE) {
            return code + " " + GeofenceStatusCodes.getStatusCodeString(code);
        }
        Throwable cause = e.getCause();
        return cause != null ? cause.getClass().getSimpleName() : e.getClass().getSimpleName();
    }

    /** Play services status code of the failure, or {@link #NO_STATUS_CODE}. */
    private static int statusCode(ExecutionException e) {
        Throwable cause = e.getCause();
        return cause instanceof ApiException ? ((ApiException) cause).getStatusCode() : NO_STATUS_CODE;
    }

    private static void logSyncFailure(Context context, String reason) {
        logSystemNow(context, context.getString(R.string.log_sync_failed, reason));
    }

    /**
     * Writes the SYSTEM entry on the calling (disk) thread, so a receiver that runs this sync
     * inside goAsync() has the row stored before it calls finish().
     */
    private static void logSystemNow(Context context, String detail) {
        AppDatabase.getInstance(context).activityEventDao()
                .insert(ActivityRepository.systemEvent(ActivityEvent.SYSTEM_ZONES, detail, System.currentTimeMillis()));
    }
}
