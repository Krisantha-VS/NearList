package com.sharma.nearlist.domain;

import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Builds the Home list order (spec section 10, "Home list behaviour"). */
public final class TaskSorter {

    /** Mean Earth radius in metres. */
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private TaskSorter() {
    }

    /**
     * Tasks whose category has places come first, nearest first (by createdAt when the location
     * is unknown); tasks whose category has no place follow, by createdAt.
     *
     * @param lat            current latitude, or null if unknown
     * @param lng            current longitude, or null if unknown
     * @param filterOrNull   only tasks of this category, or all when null
     */
    public static List<TaskListItem> build(List<Task> openTasks, List<Place> places,
                                           Double lat, Double lng, Category filterOrNull) {
        boolean hasLocation = lat != null && lng != null;
        List<TaskListItem> withPlace = new ArrayList<>();
        List<TaskListItem> withoutPlace = new ArrayList<>();

        for (Task task : openTasks) {
            if (filterOrNull != null && task.category != filterOrNull) {
                continue;
            }
            Place nearest = null;
            Double nearestDistance = null;
            for (Place place : places) {
                if (place.category != task.category) {
                    continue;
                }
                if (!hasLocation) {
                    nearest = place;
                    break;
                }
                double d = distanceMeters(lat, lng, place.latitude, place.longitude);
                if (nearestDistance == null || d < nearestDistance) {
                    nearest = place;
                    nearestDistance = d;
                }
            }
            TaskListItem item = new TaskListItem(task, nearest, nearestDistance);
            (nearest == null ? withoutPlace : withPlace).add(item);
        }

        Comparator<TaskListItem> byCreatedAt = Comparator.comparingLong(i -> i.task.createdAt);
        if (hasLocation) {
            withPlace.sort(Comparator.<TaskListItem>comparingDouble(i -> i.distanceMeters)
                    .thenComparing(byCreatedAt));
        } else {
            withPlace.sort(byCreatedAt);
        }
        withoutPlace.sort(byCreatedAt);

        List<TaskListItem> result = new ArrayList<>(withPlace.size() + withoutPlace.size());
        result.addAll(withPlace);
        result.addAll(withoutPlace);
        return result;
    }

    /** Great-circle distance using the haversine formula. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
