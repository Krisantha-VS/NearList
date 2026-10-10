package com.sharma.nearlist.data.model;

/**
 * Place and task category. Persisted as {@link #key()} (the enum name), so renaming a
 * constant is a schema change.
 */
public enum Category {
    GROCERY,
    FUEL,
    PHARMACY,
    OTHER;

    /** Stable storage key, e.g. "GROCERY". */
    public String key() {
        return name();
    }

    /** Inverse of {@link #key()}. Returns null for a null key; throws for an unknown one. */
    public static Category fromKey(String key) {
        return key == null ? null : Category.valueOf(key);
    }
}
