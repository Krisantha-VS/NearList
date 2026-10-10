package com.sharma.nearlist.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.sharma.nearlist.data.model.Category;

/**
 * One Activity log row. {@link #placeName} and {@link #category} are copied at log time,
 * so deleting a place keeps its history readable.
 */
@Entity(tableName = "activity_events")
public class ActivityEvent {

    public static final String TYPE_ENTER = "ENTER";
    public static final String TYPE_EXIT = "EXIT";
    public static final String TYPE_SYSTEM = "SYSTEM";

    public static final String OUTCOME_NOTIFIED = "NOTIFIED";
    public static final String OUTCOME_SUPPRESSED = "SUPPRESSED";
    public static final String OUTCOME_LOGGED = "LOGGED";
    public static final String OUTCOME_INFO = "INFO";

    /** SYSTEM event kinds, stored in {@link #reason} so the log can title the row. */
    public static final String SYSTEM_RESTART = "RESTART";
    public static final String SYSTEM_UPDATE = "UPDATE";
    public static final String SYSTEM_ZONES = "ZONES";
    public static final String SYSTEM_LOCATION_ERROR = "LOCATION_ERROR";

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;

    /** One of the TYPE_ constants. */
    @NonNull
    public String type = TYPE_SYSTEM;

    /** Null for SYSTEM events. */
    @Nullable
    public Long placeId;

    @Nullable
    public String placeName;

    @Nullable
    public Category category;

    /** One of the OUTCOME_ constants. */
    @NonNull
    public String outcome = OUTCOME_INFO;

    /**
     * SuppressReason name for SUPPRESSED events, a SYSTEM_ kind for SYSTEM events, otherwise null.
     * A SUPPRESSED event without a reason means notifications were blocked.
     */
    @Nullable
    public String reason;

    @Nullable
    public String detail;

    public int taskCount;
}
