package com.sharma.nearlist.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.sharma.nearlist.data.model.Category;

/** A saved place. Linked to tasks only through {@link #category} (no foreign key). */
@Entity(tableName = "places")
public class Place {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    @NonNull
    public Category category = Category.OTHER;

    public double latitude;

    public double longitude;

    public int radiusM;

    /** Accuracy of the GPS fix when captured; null when coordinates were typed in. */
    @Nullable
    public Float accuracyM;

    public long createdAt;

    /** Time of the last reminder at this place, 0 if never. */
    public long lastNotifiedAt;

    /** Cooldown and snooze gate: no reminder here while now &lt; quietUntil. */
    public long quietUntil;
}
