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

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.R;
import com.sharma.nearlist.ui.onboarding.OnboardingActivity;

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
                onView(withId(R.id.skip)).perform(nestedScrollTo(), click());
            }

            waitUntil(() -> onView(withId(R.id.bottomNav)).check(matches(isDisplayed())));
            assertTrue(NearlistApp.from(UiTestSupport.appContext()).getSettingsRepository().isOnboardingDone());
        }
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
