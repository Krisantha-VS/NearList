package com.sharma.nearlist.location;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.sharma.nearlist.util.PermissionHelper;

/** One-shot location fixes and the location-settings check (Fused Location Provider). */
public final class LocationHelper {

    private static final String TAG = "Nearlist";

    /** Interval used only to describe the request to the settings check. */
    private static final long SETTINGS_CHECK_INTERVAL_MS = 10_000L;

    /** Result callback, called on the main thread. */
    public interface Callback<T> {
        void onResult(@Nullable T result);
    }

    private LocationHelper() {
    }

    public static boolean hasForegroundPermission(Context context) {
        return PermissionHelper.hasForegroundLocation(context);
    }

    /** True below API 29 when foreground location is granted. */
    public static boolean hasBackgroundPermission(Context context) {
        return PermissionHelper.hasBackgroundLocation(context);
    }

    /** {@link #getCurrentLocation(Context, int, Callback)} with {@link Priority#PRIORITY_HIGH_ACCURACY}. */
    public static CancellationTokenSource getCurrentLocation(Context context, Callback<Location> callback) {
        return getCurrentLocation(context, Priority.PRIORITY_HIGH_ACCURACY, callback);
    }

    /**
     * Requests a fresh fix, falling back to the last known location. Delivers null when there is
     * no permission or no location at all. The accuracy is {@link Location#getAccuracy()}.
     *
     * @param priority a {@link Priority} constant
     * @return a source the caller can cancel (e.g. in onStop) to drop the request and its callback
     */
    @SuppressLint("MissingPermission")
    public static CancellationTokenSource getCurrentLocation(Context context, int priority,
                                                             Callback<Location> callback) {
        CancellationTokenSource cts = new CancellationTokenSource();
        if (!hasForegroundPermission(context)) {
            callback.onResult(null);
            return cts;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        try {
            client.getCurrentLocation(priority, cts.getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            callback.onResult(location);
                        } else {
                            deliverLastLocation(client, cts, callback);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "getCurrentLocation failed, using last location", e);
                        deliverLastLocation(client, cts, callback);
                    });
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission revoked", e);
            callback.onResult(null);
        }
        return cts;
    }

    @SuppressLint("MissingPermission")
    private static void deliverLastLocation(FusedLocationProviderClient client, CancellationTokenSource cts,
                                            Callback<Location> callback) {
        if (cts.getToken().isCancellationRequested()) {
            return;
        }
        try {
            client.getLastLocation()
                    .addOnSuccessListener(callback::onResult)
                    .addOnFailureListener(e -> callback.onResult(null));
        } catch (SecurityException e) {
            Log.w(TAG, "Location permission revoked", e);
            callback.onResult(null);
        }
    }

    /**
     * Checks that device location is on with high accuracy. If it can be fixed by a system dialog,
     * {@code onResolvable} gets the exception to launch with an IntentSenderRequest.
     */
    public static void checkLocationSettings(Activity activity, Runnable onEnabled,
                                             Consumer<ResolvableApiException> onResolvable,
                                             Runnable onFailed) {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, SETTINGS_CHECK_INTERVAL_MS).build();
        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(request)
                .setAlwaysShow(true)
                .build();
        LocationServices.getSettingsClient(activity)
                .checkLocationSettings(settingsRequest)
                .addOnSuccessListener(activity, response -> onEnabled.run())
                .addOnFailureListener(activity, e -> {
                    if (e instanceof ResolvableApiException) {
                        onResolvable.accept((ResolvableApiException) e);
                    } else {
                        Log.w(TAG, "Location settings check failed", e);
                        onFailed.run();
                    }
                });
    }
}
