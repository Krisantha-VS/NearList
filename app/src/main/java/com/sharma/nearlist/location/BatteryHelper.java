package com.sharma.nearlist.location;

import android.content.Context;
import android.os.BatteryManager;

/** Battery state through {@link BatteryManager} properties (no sticky broadcast). */
public final class BatteryHelper {

    private BatteryHelper() {
    }

    /** Battery level 0–100, or -1 if the device doesn't report it. */
    public static int getLevelPercent(Context context) {
        BatteryManager bm = context.getSystemService(BatteryManager.class);
        if (bm == null) {
            return -1;
        }
        int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return level >= 0 && level <= 100 ? level : -1;
    }

    public static boolean isCharging(Context context) {
        BatteryManager bm = context.getSystemService(BatteryManager.class);
        return bm != null && bm.isCharging();
    }
}
