package com.sharma.nearlist.domain;

import com.sharma.nearlist.data.entity.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of {@link ReminderDecisionEngine#decide}. */
public final class Decision {

    private final boolean notify;
    private final SuppressReason reason;
    private final String detail;
    private final List<Task> tasks;

    private Decision(boolean notify, SuppressReason reason, String detail, List<Task> tasks) {
        this.notify = notify;
        this.reason = reason;
        this.detail = detail;
        this.tasks = tasks;
    }

    /** One grouped notification for these tasks. */
    public static Decision notify(List<Task> tasks) {
        return new Decision(true, null, null, Collections.unmodifiableList(new ArrayList<>(tasks)));
    }

    /** @param detail human-readable context for the Activity log, or null */
    public static Decision suppress(SuppressReason reason, String detail) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        return new Decision(false, reason, detail, Collections.emptyList());
    }

    public boolean isNotify() {
        return notify;
    }

    /** Null when {@link #isNotify()}. */
    public SuppressReason getReason() {
        return reason;
    }

    /** Null when {@link #isNotify()} or when there is no extra context. */
    public String getDetail() {
        return detail;
    }

    /** Tasks to notify about; empty when suppressed. */
    public List<Task> getTasks() {
        return tasks;
    }
}
