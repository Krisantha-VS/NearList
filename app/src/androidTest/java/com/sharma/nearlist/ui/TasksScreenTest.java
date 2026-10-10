package com.sharma.nearlist.ui;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.sharma.nearlist.ui.UiTestSupport.waitUntil;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.sharma.nearlist.R;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.ui.main.MainActivity;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Tasks tab: adding a task through the form, and the category filter chips. */
@RunWith(AndroidJUnit4.class)
public class TasksScreenTest {

    private static final String GROCERY_TASK = "Buy milk and eggs";
    private static final String FUEL_TASK = "Refill petrol";

    @Rule
    public GrantPermissionRule permissions = UiTestSupport.grantAppPermissions();

    @Before
    public void setUp() {
        UiTestSupport.resetApp(true);
    }

    @Test
    public void addTask_savesTaskAndShowsItInList() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.addTask)).perform(click());

            onView(withId(R.id.titleInput)).perform(replaceText("Pick up parcel"));
            onView(withId(R.id.chipOther)).perform(click());
            closeSoftKeyboard();
            onView(withId(R.id.action_save)).perform(click());

            waitUntil(() -> onView(withText("Pick up parcel")).check(matches(isDisplayed())));
        }
        List<Task> open = AppDatabase.getInstance(UiTestSupport.appContext()).taskDao()
                .getOpenByCategorySync(Category.OTHER.key());
        assertEquals(1, open.size());
        assertEquals("Pick up parcel", open.get(0).title);
    }

    @Test
    public void categoryFilter_showsOnlyTasksOfSelectedCategory() {
        insertTask(GROCERY_TASK, Category.GROCERY, 1_000L);
        insertTask(FUEL_TASK, Category.FUEL, 2_000L);

        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            waitUntil(() -> onView(withText(GROCERY_TASK)).check(matches(isDisplayed())));
            onView(withText(FUEL_TASK)).check(matches(isDisplayed()));

            onView(filterChip(R.id.chipFuel)).perform(click());
            waitUntil(() -> onView(withText(GROCERY_TASK)).check(doesNotExist()));
            onView(withText(FUEL_TASK)).check(matches(isDisplayed()));

            onView(filterChip(R.id.chipGrocery)).perform(click());
            waitUntil(() -> onView(withText(FUEL_TASK)).check(doesNotExist()));
            onView(withText(GROCERY_TASK)).check(matches(isDisplayed()));

            onView(filterChip(R.id.chipAll)).perform(click());
            waitUntil(() -> onView(withText(FUEL_TASK)).check(matches(isDisplayed())));
            onView(withText(GROCERY_TASK)).check(matches(isDisplayed()));
        }
    }

    /**
     * The Activity tab is hidden but still in the hierarchy, with its own filterChips/chipAll,
     * so match only the chip that is on screen (the Tasks tab's).
     */
    private static Matcher<View> filterChip(int chipId) {
        return allOf(withId(chipId), isDisplayed());
    }

    private static void insertTask(String title, Category category, long createdAt) {
        Task task = new Task();
        task.title = title;
        task.category = category;
        task.createdAt = createdAt;
        AppDatabase.getInstance(UiTestSupport.appContext()).taskDao().insert(task);
    }
}
