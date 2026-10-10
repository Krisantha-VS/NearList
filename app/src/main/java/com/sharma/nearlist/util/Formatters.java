package com.sharma.nearlist.util;

import android.content.Context;
import android.text.format.DateFormat;

import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.domain.TimeWindow;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Display formatting shared by the UI. */
public final class Formatters {

    private Formatters() {
    }

    /** Above this many whole kilometres the distance is shown as "500+ km". */
    static final int MAX_DISPLAY_KM = 500;

    /**
     * "450 m" below 1 km, "1.2 km" (one decimal) below 10 km, "14 km" (whole kilometres) up to
     * {@link #MAX_DISPLAY_KM}, and "500+ km" beyond. A decimal only helps at walking or short-drive
     * range; beyond 500 km no saved place is a realistic errand (it also covers all of Sri Lanka),
     * and an exact "14612 km" (e.g. an emulator still at its default location) is just noise.
     * Sorting is unaffected: it uses the raw distance.
     */
    public static String distance(double meters) {
        long rounded = Math.round(meters);
        if (rounded < 1000) {
            return rounded + " m";
        }
        long tenths = Math.round(meters / 100.0);
        if (tenths < 100) {
            return String.format(Locale.US, "%.1f km", tenths / 10.0);
        }
        long km = Math.round(meters / 1000.0);
        if (km > MAX_DISPLAY_KM) {
            return MAX_DISPLAY_KM + "+ km";
        }
        return km + " km";
    }

    /** Place radius: "300 m", "1 km" (whole kilometres without a decimal), "1.5 km". */
    public static String radius(int meters) {
        if (meters < 1000) {
            return meters + " m";
        }
        if (meters % 1000 == 0) {
            return (meters / 1000) + " km";
        }
        return String.format(Locale.US, "%.1f km", meters / 1000.0);
    }

    /** One coordinate with 4 decimals (about 11 m), always with a dot: "6.5854". */
    public static String coordinate(double value) {
        return String.format(Locale.US, "%.4f", value);
    }

    /** "6–9 PM" for a task with a time window, otherwise null. */
    public static String timeWindow(Task task) {
        return task.hasTimeWindow ? TimeWindow.format(task.windowStartMin, task.windowEndMin) : null;
    }

    /** "6:00 PM" (or "18:00") for minutes since midnight, in the device's 12/24-hour format. */
    public static String timeOfDay(Context context, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
        calendar.set(Calendar.MINUTE, minutes % 60);
        return DateFormat.getTimeFormat(context).format(calendar.getTime());
    }

    /** "Done · 9:42 AM", in the device's 12/24-hour format. */
    public static String doneAt(Context context, long completedAt) {
        String time = DateFormat.getTimeFormat(context).format(new Date(completedAt));
        return context.getString(R.string.tasks_done_at, time);
    }
}
