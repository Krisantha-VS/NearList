package com.sharma.nearlist.domain;

import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;

/** One open task on the Home list, with the closest place of its category. */
public final class TaskListItem {

    public final Task task;

    /**
     * Closest place of the task's category, or null when the category has no places.
     * When the current location is unknown this is the first place of the category.
     */
    public final Place nearestPlace;

    /** Metres to {@link #nearestPlace}; null when there is no place or no current location. */
    public final Double distanceMeters;

    public TaskListItem(Task task, Place nearestPlace, Double distanceMeters) {
        this.task = task;
        this.nearestPlace = nearestPlace;
        this.distanceMeters = distanceMeters;
    }
}
