package com.sharma.nearlist.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.sharma.nearlist.ui.UiTestSupport.nestedScrollTo;
import static com.sharma.nearlist.ui.UiTestSupport.waitUntil;

import static org.junit.Assert.assertTrue;

import android.os.Build;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.viewpager2.widget.ViewPager2;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.R;
import com.sharma.nearlist.ui.onboarding.OnboardingActivity;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * First-run flow. Permissions are pre-granted, so the Location and Background steps complete
 * themselves in onResume; on API 33+ the Notifications step still needs an answer.
 */
@RunWith(AndroidJUnit4.class)
public class OnboardingFlowTest {

    @Rule
    public GrantPermissionRule permissions = UiTestSupport.grantAppPermissions();

    @Before
    public void setUp() {
        UiTestSupport.resetApp(false);
    }

    @Test
    public void welcomeStep_rendersFirstStepOfFlow() {
        try (ActivityScenario<OnboardingActivity> ignored = ActivityScenario.launch(OnboardingActivity.class)) {
            onView(withId(R.id.stepCounter)).check(matches(withText("Step 1 of " + stepCount())));
            onView(withId(R.id.stepName)).check(matches(withText(R.string.onboarding_welcome_label)));
            onView(withId(R.id.getStarted)).perform(nestedScrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test
    public void getStarted_advancesThroughGrantedSteps_andOpensHome() {
        try (ActivityScenario<OnboardingActivity> ignored = ActivityScenario.launch(OnboardingActivity.class)) {
            onView(withId(R.id.getStarted)).perform(nestedScrollTo(), click());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int count = stepCount();
                waitUntil(() -> onView(withId(R.id.stepCounter))
                        .check(matches(withText("Step " + count + " of " + count))));
                onView(withId(R.id.stepName)).check(matches(withText(R.string.onboarding_notifications_label)));
                // The counter updates in onPageSelected, i.e. when the smooth scroll starts. Location
                // and Background auto-advance back to back, so wait for the pager to settle on the
                // last page before touching its views, or Skip can still be partly off screen.
                waitUntil(() -> onView(withId(R.id.pager)).check(matches(pagerSettledOn(count - 1))));
                onView(withId(R.id.skip)).perform(nestedScrollTo(), click());
            }

            waitUntil(() -> onView(withId(R.id.bottomNav)).check(matches(isDisplayed())));
            assertTrue(NearlistApp.from(UiTestSupport.appContext()).getSettingsRepository().isOnboardingDone());
        }
    }

    /** Matches a ViewPager2 that shows {@code position} and is not scrolling. */
    private static Matcher<View> pagerSettledOn(int position) {
        return new BoundedMatcher<View, ViewPager2>(ViewPager2.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("ViewPager2 idle on page " + position);
            }

            @Override
            protected boolean matchesSafely(ViewPager2 pager) {
                return pager.getCurrentItem() == position
                        && pager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE;
            }
        };
    }

    /** Mirrors OnboardingActivity's step list for this API level. */
    private static int stepCount() {
        int count = 2;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            count++;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            count++;
        }
        return count;
    }
}
