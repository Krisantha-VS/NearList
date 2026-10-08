package com.sharma.nearlist.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TaskSorterTest {

    // User standing near Keells Kalutara.
    private static final double USER_LAT = 6.5850;
    private static final double USER_LNG = 79.9600;

    private final Place keells = place(1, "Keells Kalutara", Category.GROCERY, 6.5854, 79.9607);
    private final Place cargills = place(2, "Cargills Panadura", Category.GROCERY, 6.7132, 79.9026);
    private final Place ceypetco = place(3, "Ceypetco Kalutara North", Category.FUEL, 6.5950, 79.9590);

    // U08
    @Test
    public void build_usesDistanceToNearerPlace_whenCategoryHasTwoPlaces() {
        Task milk = task("Buy milk and eggs", Category.GROCERY, 1);

        List<TaskListItem> items = TaskSorter.build(List.of(milk), Arrays.asList(cargills, keells),
                USER_LAT, USER_LNG, null);

        assertEquals(1, items.size());
        assertSame(keells, items.get(0).nearestPlace);
        assertNotNull(items.get(0).distanceMeters);
        // About 89 m: 0.0004° of latitude and 0.0007° of longitude at 6.6° N.
        assertEquals(89.0, items.get(0).distanceMeters, 2.0);
    }

    @Test
    public void build_sortsNearestFirst() {
        Task petrol = task("Refill petrol", Category.FUEL, 1);
        Task milk = task("Buy milk and eggs", Category.GROCERY, 2);

        List<TaskListItem> items = TaskSorter.build(Arrays.asList(petrol, milk),
                Arrays.asList(keells, ceypetco), USER_LAT, USER_LNG, null);

        assertSame(milk, items.get(0).task);
        assertSame(petrol, items.get(1).task);
    }

    // U09
    @Test
    public void build_putsTaskLastWithNullDistance_whenCategoryHasNoPlaces() {
        Task book = task("Return library book", Category.OTHER, 1);
        Task milk = task("Buy milk and eggs", Category.GROCERY, 2);

        List<TaskListItem> items = TaskSorter.build(Arrays.asList(book, milk),
                List.of(keells), USER_LAT, USER_LNG, null);

        assertSame(milk, items.get(0).task);
        assertSame(book, items.get(1).task);
        assertNull(items.get(1).nearestPlace);
        assertNull(items.get(1).distanceMeters);
    }

    // U10
    @Test
    public void build_sortsByCreatedAtWithoutDistance_whenLocationUnknown() {
        Task petrol = task("Refill petrol", Category.FUEL, 1);
        Task milk = task("Buy milk and eggs", Category.GROCERY, 2);
        Task bread = task("Bread", Category.GROCERY, 3);

        List<TaskListItem> items = TaskSorter.build(Arrays.asList(bread, milk, petrol),
                Arrays.asList(keells, ceypetco), null, null, null);

        assertSame(petrol, items.get(0).task);
        assertSame(milk, items.get(1).task);
        assertSame(bread, items.get(2).task);
        for (TaskListItem item : items) {
            assertNull(item.distanceMeters);
            assertNotNull(item.nearestPlace);
        }
    }

    @Test
    public void build_keepsOnlyFilteredCategory() {
        Task petrol = task("Refill petrol", Category.FUEL, 1);
        Task milk = task("Buy milk and eggs", Category.GROCERY, 2);

        List<TaskListItem> items = TaskSorter.build(Arrays.asList(petrol, milk),
                Arrays.asList(keells, ceypetco), USER_LAT, USER_LNG, Category.FUEL);

        assertEquals(1, items.size());
        assertSame(petrol, items.get(0).task);
    }

    @Test
    public void build_returnsEmptyList_whenNoTasks() {
        assertEquals(0, TaskSorter.build(Collections.emptyList(), List.of(keells),
                USER_LAT, USER_LNG, null).size());
    }

    private static Place place(long id, String name, Category category, double lat, double lng) {
        Place place = new Place();
        place.id = id;
        place.name = name;
        place.category = category;
        place.latitude = lat;
        place.longitude = lng;
        place.radiusM = 300;
        return place;
    }

    private static Task task(String title, Category category, long createdAt) {
        Task task = new Task();
        task.title = title;
        task.category = category;
        task.createdAt = createdAt;
        return task;
    }
}
