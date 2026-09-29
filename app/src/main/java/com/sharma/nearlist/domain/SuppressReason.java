package com.sharma.nearlist.domain;

/** Why an ENTER event did not produce a notification. Stored as the enum name in the Activity log. */
public enum SuppressReason {
    NO_OPEN_TASKS,
    COOLDOWN,
    OUTSIDE_TIME_WINDOW
}
