package com.sharma.nearlist.debug;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.sharma.nearlist.util.PermissionHelper;

/**
 * Debug "Keep location active": balanced-power location updates every 10 s, so the fused
 * provider on the emulator keeps computing locations and evaluating geofences. Process-wide, so
 * it survives rotation; MainActivity stops it when it finishes. Not persisted.
 */
public final class KeepLocationActive {

    private static final String TAG = "Nearlist";
    private static final long INTERVAL_MS = 10_000L;

    private static FusedLocationProviderClient client;

    private static final LocationCallback CALLBACK = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult result) {
            Location location = result.getLastLocation();
            if (location != null) {
                Log.d(TAG, "Keep location active: " + location.getLatitude() + ", " + location.getLongitude());
            }
        }
    };

    private KeepLocationActive() {
    }

    @MainThread
    public static boolean isActive() {
        return client != null;
    }

    /** @return false if foreground location is not granted, so nothing was started */
    @MainThread
    @SuppressLint("MissingPermission")
    public static boolean start(Context context) {
        if (client != null) {
            return true;
        }
        Context appContext = context.getApplicationContext();
        if (!PermissionHelper.hasForegroundLocation(appContext)) {
            return false;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
                .build();
        client = LocationServices.getFusedLocationProviderClient(appContext);
        client.requestLocationUpdates(request, CALLBACK, Looper.getMainLooper())
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Keep location active: request failed", e);
                    stop();
                });
        Log.i(TAG, "Keep location active: started, balanced power, every " + INTERVAL_MS + " ms");
        return true;
    }

    @MainThread
    public static void stop() {
        if (client == null) {
            return;
        }
        client.removeLocationUpdates(CALLBACK);
        client = null;
        Log.i(TAG, "Keep location active: stopped");
    }
}
