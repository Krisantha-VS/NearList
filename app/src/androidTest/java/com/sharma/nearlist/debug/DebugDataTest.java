package com.sharma.nearlist.debug;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** "Load sample data" must give the same result however often it is used. */
@RunWith(AndroidJUnit4.class)
public class DebugDataTest {

    private AppDatabase db;

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void replaceWithSamples_once_insertsThreePlacesAndFiveTasks() {
        DebugData.replaceWithSamples(db);

        assertEquals(3, db.placeDao().countSync());
        assertEquals(5, count("tasks"));
    }

    @Test
    public void replaceWithSamples_repeated_doesNotDuplicate() {
        DebugData.replaceWithSamples(db);
        DebugData.replaceWithSamples(db);
        DebugData.replaceWithSamples(db);

        assertEquals(3, db.placeDao().countSync());
        assertEquals(5, count("tasks"));
        // "Buy milk and eggs" is the only open grocery task; "Buy bread" is completed.
        assertEquals(1, db.taskDao().getOpenByCategorySync(Category.GROCERY.key()).size());
    }

    @Test
    public void replaceWithSamples_removesUserDataAndLog() {
        Place place = new Place();
        place.name = "My shop";
        place.category = Category.OTHER;
        db.placeDao().insert(place);
        Task task = new Task();
        task.title = "My task";
        task.category = Category.OTHER;
        db.taskDao().insert(task);
        db.activityEventDao().insert(new ActivityEvent());

        DebugData.replaceWithSamples(db);

        assertEquals(3, db.placeDao().countSync());
        assertEquals(5, count("tasks"));
        assertEquals(0, count("activity_events"));
    }

    private int count(String table) {
        try (Cursor cursor = db.query("SELECT COUNT(*) FROM " + table, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
