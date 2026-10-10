package com.sharma.nearlist.ui;

import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;

import static org.hamcrest.Matchers.allOf;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import androidx.core.widget.NestedScrollView;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.ScrollToAction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.data.db.AppDatabase;

import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.List;

/** Shared set-up for the Espresso tests: permissions, a clean database and polling waits. */
public final class UiTestSupport {

    /** Room writes run on AppExecutors.diskIO(), which Espresso does not track, so tests poll. */
    private static final long WAIT_TIMEOUT_MS = 15_000L;
    private static final long WAIT_STEP_MS = 100L;

    private UiTestSupport() {
    }

    /**
     * Grants every runtime permission the app asks for on this API level, so no system dialog
     * appears and the geofence sync on app open can succeed.
     */
    public static GrantPermissionRule grantAppPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return GrantPermissionRule.grant(permissions.toArray(new String[0]));
    }

    public static Context appContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    }

    /** Empties all tables of the app database and sets the onboarding flag. Not on the main thread. */
    public static void resetApp(boolean onboardingDone) {
        Context context = appContext();
        AppDatabase.getInstance(context).clearAllTables();
        NearlistApp.from(context).getSettingsRepository().setOnboardingDone(onboardingDone);
    }

    /** Retries {@code check} until it passes or the timeout expires, then rethrows the last failure. */
    public static void waitUntil(Runnable check) {
        long deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS;
        while (true) {
            try {
                check.run();
                return;
            } catch (Throwable failure) {
                if (SystemClock.uptimeMillis() > deadline) {
                    throw failure;
                }
                SystemClock.sleep(WAIT_STEP_MS);
            }
        }
    }

    /** Espresso 3.5's scrollTo() only supports ScrollView; the app's forms use NestedScrollView. */
    public static ViewAction nestedScrollTo() {
        return new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return allOf(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE),
                        isDescendantOfA(isAssignableFrom(NestedScrollView.class)));
            }

            @Override
            public String getDescription() {
                return "scroll to view inside a NestedScrollView";
            }

            @Override
            public void perform(UiController uiController, View view) {
                new ScrollToAction().perform(uiController, view);
            }
        };
    }
}
