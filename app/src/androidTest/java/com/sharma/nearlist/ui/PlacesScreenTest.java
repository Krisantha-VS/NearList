package com.sharma.nearlist.ui;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.sharma.nearlist.ui.UiTestSupport.nestedScrollTo;
import static com.sharma.nearlist.ui.UiTestSupport.waitUntil;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.sharma.nearlist.R;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.ui.main.MainActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Places tab: adding a place with typed coordinates (no GPS needed). */
@RunWith(AndroidJUnit4.class)
public class PlacesScreenTest {

    private static final String PLACE_NAME = "Osu Sala Kalutara";

    @Rule
    public GrantPermissionRule permissions = UiTestSupport.grantAppPermissions();

    @Before
    public void setUp() {
        UiTestSupport.resetApp(true);
    }

    @Test
    public void addPlace_withManualCoordinates_savesAndListsPlace() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.nav_places)).perform(click());
            // With no places the FAB is hidden and the empty state offers "Add place".
            waitUntil(() -> onView(withId(R.id.emptyAddPlace))
                    .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE))));
            onView(withId(R.id.emptyAddPlace)).perform(nestedScrollTo(), click());

            // A new place starts a GPS fix; wait until it has finished (fix or no fix) so it
            // can't overwrite the typed coordinates halfway through.
            waitUntil(() -> onView(withId(R.id.useLocation)).check(matches(isEnabled())));
            if (!isDisplayedNow(R.id.manualSection)) {
                onView(withId(R.id.manualToggle)).perform(nestedScrollTo(), click());
            }
            onView(withId(R.id.latitudeInput)).perform(nestedScrollTo(), replaceText("6.5800"));
            onView(withId(R.id.longitudeInput)).perform(nestedScrollTo(), replaceText("79.9600"));
            onView(withId(R.id.nameInput)).perform(nestedScrollTo(), replaceText(PLACE_NAME));
            onView(withId(R.id.chipPharmacy)).perform(nestedScrollTo(), click());
            closeSoftKeyboard();
            onView(withId(R.id.action_save)).perform(click());

            waitUntil(() -> onView(withText(PLACE_NAME)).check(matches(isDisplayed())));
        }
        List<Place> places = AppDatabase.getInstance(UiTestSupport.appContext()).placeDao().getAllSync();
        assertEquals(1, places.size());
        assertEquals(PLACE_NAME, places.get(0).name);
        assertEquals(Category.PHARMACY, places.get(0).category);
    }

    private static boolean isDisplayedNow(int viewId) {
        try {
            onView(withId(viewId)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
            return true;
        } catch (AssertionError e) {
            return false;
        }
    }
}
