package com.sharma.nearlist.data.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TaskDaoTest {

    private AppDatabase db;
    private TaskDao dao;

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.taskDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void getOpenByCategorySync_returnsOnlyOpenTasksOfThatCategory_oldestFirst() {
        long milk = dao.insert(task("Buy milk and eggs", Category.GROCERY, 100));
        long bread = dao.insert(task("Bread", Category.GROCERY, 200));
        dao.insert(task("Refill petrol", Category.FUEL, 300));
        Task doneGrocery = task("Buy rice", Category.GROCERY, 50);
        doneGrocery.done = true;
        doneGrocery.completedAt = 400;
        dao.insert(doneGrocery);

        List<Task> open = dao.getOpenByCategorySync(Category.GROCERY.key());

        assertEquals(2, open.size());
        assertEquals(milk, open.get(0).id);
        assertEquals(bread, open.get(1).id);
        assertEquals(Category.GROCERY, open.get(0).category);
    }

    @Test
    public void markDone_setsDoneAndCompletedAt_onlyForGivenIds() {
        long milk = dao.insert(task("Buy milk and eggs", Category.GROCERY, 100));
        long bread = dao.insert(task("Bread", Category.GROCERY, 200));
        long eggs = dao.insert(task("Eggs", Category.GROCERY, 300));

        dao.markDone(new long[]{milk, bread}, 5_000L);

        Task doneMilk = dao.getByIdSync(milk);
        Task doneBread = dao.getByIdSync(bread);
        Task stillOpen = dao.getByIdSync(eggs);
        assertTrue(doneMilk.done);
        assertEquals(5_000L, doneMilk.completedAt);
        assertTrue(doneBread.done);
        assertEquals(5_000L, doneBread.completedAt);
        assertFalse(stillOpen.done);
        assertEquals(0L, stillOpen.completedAt);

        List<Task> open = dao.getOpenByCategorySync(Category.GROCERY.key());
        assertEquals(1, open.size());
        assertEquals(eggs, open.get(0).id);
    }

    private static Task task(String title, Category category, long createdAt) {
        Task task = new Task();
        task.title = title;
        task.category = category;
        task.createdAt = createdAt;
        return task;
    }
}
