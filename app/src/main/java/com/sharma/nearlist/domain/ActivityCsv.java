package com.sharma.nearlist.domain;

import com.sharma.nearlist.data.entity.ActivityEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Activity log as CSV (RFC 4180) for the "Export log" share. Pure Java, so it is unit tested. */
public final class ActivityCsv {

    static final String HEADER = "timestamp,type,place,category,outcome,reason,detail,taskCount";
    private static final String LINE_END = "\r\n";

    private ActivityCsv() {
    }

    /**
     * @param events   rows in the order they should appear
     * @param timeZone zone for the ISO-8601 timestamps, e.g. "2026-10-11T18:42:00+05:30"
     */
    public static String build(List<ActivityEvent> events, TimeZone timeZone) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        iso.setTimeZone(timeZone);
        StringBuilder csv = new StringBuilder(HEADER).append(LINE_END);
        for (ActivityEvent event : events) {
            csv.append(iso.format(new Date(event.timestamp))).append(',')
                    .append(field(event.type)).append(',')
                    .append(field(event.placeName)).append(',')
                    .append(field(event.category != null ? event.category.key() : null)).append(',')
                    .append(field(event.outcome)).append(',')
                    .append(field(event.reason)).append(',')
                    .append(field(event.detail)).append(',')
                    .append(event.taskCount)
                    .append(LINE_END);
        }
        return csv.toString();
    }

    /** Empty for null; quoted, with doubled quotes, when it contains a comma, quote or line break. */
    static String field(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
