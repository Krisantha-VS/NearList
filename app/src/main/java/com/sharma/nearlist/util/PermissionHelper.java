package com.sharma.nearlist.util;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

/** Permission checks and intents to the system settings pages where the user can change them. */
public final class PermissionHelper {

    private PermissionHelper() {
    }

    public static boolean hasFineLocation(Context context) {
        return isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public static boolean hasCoarseLocation(Context context) {
        return isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    /** Precise or approximate foreground location. */
    public static boolean hasForegroundLocation(Context context) {
        return hasFineLocation(context) || hasCoarseLocation(context);
    }

    /** "Allow all the time". Below API 29 it is implied by foreground location. */
    public static boolean hasBackgroundLocation(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        return hasForegroundLocation(context);
    }

    /** POST_NOTIFICATIONS runtime permission; always true below API 33. */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return isGranted(context, Manifest.permission.POST_NOTIFICATIONS);
        }
        return true;
    }

    /** The app's page in system Settings (permissions, including "Allow all the time"). */
    public static Intent appDetailsSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** The app's notification settings page; the app details page below API 26. */
    public static Intent appNotificationSettingsIntent(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return appDetailsSettingsIntent(context);
    }

    private static boolean isGranted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
