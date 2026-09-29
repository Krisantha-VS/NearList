package com.sharma.nearlist.domain;

/** Daily time window in minutes since midnight. A window may cross midnight (22:00–02:00). */
public final class TimeWindow {

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final String EN_DASH = "–";

    private TimeWindow() {
    }

    /**
     * Whether {@code nowMin} falls in the window. Start is inclusive, end exclusive;
     * {@code start == end} means all day.
     */
    public static boolean contains(int startMin, int endMin, int nowMin) {
        if (startMin == endMin) {
            return true;
        }
        if (startMin < endMin) {
            return nowMin >= startMin && nowMin < endMin;
        }
        return nowMin >= startMin || nowMin < endMin;
    }

    /**
     * Compact 12-hour text: "6–9 PM", "8 AM–8 PM", "10:30 PM–2 AM". The AM/PM marker is written
     * once when both ends share it, and minutes are omitted on the hour.
     */
    public static String format(int startMin, int endMin) {
        int start = normalize(startMin);
        int end = normalize(endMin);
        String startMarker = marker(start);
        String endMarker = marker(end);
        if (startMarker.equals(endMarker)) {
            return clock(start) + EN_DASH + clock(end) + " " + endMarker;
        }
        return clock(start) + " " + startMarker + EN_DASH + clock(end) + " " + endMarker;
    }

    private static int normalize(int minutes) {
        return ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
    }

    private static String marker(int minutes) {
        return minutes < 12 * 60 ? "AM" : "PM";
    }

    private static String clock(int minutes) {
        int hour24 = minutes / 60;
        int minute = minutes % 60;
        int hour12 = hour24 % 12 == 0 ? 12 : hour24 % 12;
        return minute == 0 ? Integer.toString(hour12) : hour12 + ":" + (minute < 10 ? "0" : "") + minute;
    }
}
