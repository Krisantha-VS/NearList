package com.sharma.nearlist.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.sharma.nearlist.data.model.Category;

/** A to-do item, reminded at any saved place of the same {@link #category}. */
@Entity(tableName = "tasks")
public class Task {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String title = "";

    @NonNull
    public Category category = Category.OTHER;

    @Nullable
    public String notes;

    public boolean hasTimeWindow;

    /** Minutes since midnight (18:00 = 1080). May be greater than the end (window crosses midnight). */
    public int windowStartMin;

    public int windowEndMin;

    public boolean done;

    /** 0 while open. */
    public long completedAt;

    public long createdAt;
}
