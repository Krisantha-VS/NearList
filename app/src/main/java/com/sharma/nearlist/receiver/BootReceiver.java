package com.sharma.nearlist.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.sharma.nearlist.R;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.repo.ActivityRepository;
import com.sharma.nearlist.location.GeofenceManager;
import com.sharma.nearlist.util.AppExecutors;

/**
 * Geofences don't survive a reboot or an app update, so re-register them
 * and log how many zones are active again.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "Nearlist";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean update = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!boot && !update) {
            return;
        }
        Context appContext = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        AppExecutors.diskIO().execute(() -> {
            try {
                int zones = GeofenceManager.syncAllBlocking(appContext, GeofenceManager.NO_CHANGED_PLACE);
                int message = boot ? R.plurals.log_reregistered_after_restart : R.plurals.log_reregistered_after_update;
                String detail = appContext.getResources().getQuantityString(message, zones, zones);
                AppDatabase.getInstance(appContext).activityEventDao()
                        .insert(ActivityRepository.systemEvent(
                                boot ? ActivityEvent.SYSTEM_RESTART : ActivityEvent.SYSTEM_UPDATE,
                                detail, System.currentTimeMillis()));
                Log.i(TAG, detail);
            } catch (RuntimeException e) {
                Log.e(TAG, "Re-registration failed after " + action, e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
