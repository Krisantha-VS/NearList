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

    /** "450 m" below 1 km, otherwise kilometres with one decimal: "1.2 km". */
    public static String distance(double meters) {
        long rounded = Math.round(meters);
        if (rounded < 1000) {
            return rounded + " m";
        }
        return String.format(Locale.US, "%.1f km", meters / 1000.0);
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
