package com.sharma.nearlist.ui.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.BuildConfig;
import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.R;
import com.sharma.nearlist.databinding.ActivityMainBinding;
import com.sharma.nearlist.debug.KeepLocationActive;
import com.sharma.nearlist.location.GeofenceManager;
import com.sharma.nearlist.ui.activity.ActivityLogFragment;
import com.sharma.nearlist.ui.onboarding.OnboardingActivity;
import com.sharma.nearlist.ui.places.PlacesFragment;
import com.sharma.nearlist.ui.settings.SettingsActivity;
import com.sharma.nearlist.ui.tasks.AddEditTaskActivity;
import com.sharma.nearlist.ui.tasks.TasksFragment;

/**
 * Main shell: toolbar, three tabs (Tasks, Places, Activity) switched with show/hide so each
 * fragment keeps its state, and the bottom navigation.
 */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_TAB = "tab";
    private static final String TAG_TASKS = "tab_tasks";
    private static final String TAG_PLACES = "tab_places";
    private static final String TAG_ACTIVITY = "tab_activity";
    private static final int REQUEST_PLAY_SERVICES = 1;

    private ActivityMainBinding binding;
    @IdRes
    private int currentTab = R.id.nav_tasks;
    /** Export and Clear are offered only while the Activity log has entries (none when empty). */
    private boolean activityLogHasEvents;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!NearlistApp.from(this).getSettingsRepository().isOnboardingDone()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);
            binding.toolbar.setPadding(0, bars.top, 0, 0);
            // BottomNavigationView adds the bottom inset itself.
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragmentContainer, new TasksFragment(), TAG_TASKS)
                    .add(R.id.fragmentContainer, new PlacesFragment(), TAG_PLACES)
                    .add(R.id.fragmentContainer, new ActivityLogFragment(), TAG_ACTIVITY)
                    .commitNow();
        } else {
            currentTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_tasks);
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            } else if (item.getItemId() == R.id.action_export_log) {
                activityFragment().exportLog();
                return true;
            } else if (item.getItemId() == R.id.action_clear_log) {
                activityFragment().confirmClearLog();
                return true;
            }
            return onDebugMenuItem(item);
        });
        setUpOverflowIcons(binding.toolbar.getMenu());
        binding.bottomNav.setSelectedItemId(currentTab);
        showTab(currentTab);

        if (savedInstanceState == null) {
            checkPlayServices();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Cheap self-healing: restores zones after location toggles,
        // battery band changes or Play services data being cleared. Skipped right after a
        // successful sync, so a just-saved place keeps its INITIAL_TRIGGER_ENTER registration.
        GeofenceManager.syncAllOnAppOpen(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // "Keep location active" lasts until the app is closed, not across rotation.
        if (isFinishing()) {
            KeepLocationActive.stop();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // A reminder notification was tapped: show the task list.
        binding.bottomNav.setSelectedItemId(R.id.nav_tasks);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    /** Switches to the Places tab (empty-state "Save a place"). */
    public void showPlacesTab() {
        binding.bottomNav.setSelectedItemId(R.id.nav_places);
    }

    /** Opens the task form for {@code taskId}, or for a new task with {@link AddEditTaskActivity#NEW_TASK}. */
    public void openTaskEditor(long taskId) {
        startActivity(AddEditTaskActivity.newIntent(this, taskId));
    }

    /** Called by the Activity tab whenever the log goes from empty to non-empty or back. */
    public void setActivityLogHasEvents(boolean hasEvents) {
        activityLogHasEvents = hasEvents;
        updateMenu();
    }

    private void showTab(@IdRes int tabId) {
        currentTab = tabId;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        setShown(transaction, fm.findFragmentByTag(TAG_TASKS), tabId == R.id.nav_tasks);
        setShown(transaction, fm.findFragmentByTag(TAG_PLACES), tabId == R.id.nav_places);
        setShown(transaction, fm.findFragmentByTag(TAG_ACTIVITY), tabId == R.id.nav_activity);
        transaction.commit();

        if (tabId == R.id.nav_places) {
            binding.toolbar.setTitle(R.string.title_places);
        } else if (tabId == R.id.nav_activity) {
            binding.toolbar.setTitle(R.string.title_activity);
        } else {
            binding.toolbar.setTitle(R.string.app_name);
        }
        updateMenu();
    }

    private void updateMenu() {
        Menu menu = binding.toolbar.getMenu();
        boolean tasks = currentTab == R.id.nav_tasks;
        boolean log = currentTab == R.id.nav_activity && activityLogHasEvents;
        menu.findItem(R.id.action_settings).setVisible(tasks);
        menu.findItem(R.id.action_export_log).setVisible(log);
        menu.findItem(R.id.action_clear_log).setVisible(log);
        boolean debug = tasks && BuildConfig.DEBUG;
        menu.findItem(R.id.action_load_sample_data).setVisible(debug);
        menu.findItem(R.id.action_simulate_arrival).setVisible(debug);
        menu.findItem(R.id.action_keep_location_active).setVisible(debug)
                .setChecked(KeepLocationActive.isActive());
        menu.findItem(R.id.action_clear_all_data).setVisible(debug);
    }

    /** Debug-build tools in the Tasks overflow menu (hidden in release builds). */
    private boolean onDebugMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (!BuildConfig.DEBUG) {
            return false;
        }
        if (id == R.id.action_load_sample_data) {
            tasksFragment().loadSampleData();
        } else if (id == R.id.action_simulate_arrival) {
            tasksFragment().simulateArrival();
        } else if (id == R.id.action_clear_all_data) {
            tasksFragment().confirmClearAllData();
        } else if (id == R.id.action_keep_location_active) {
            toggleKeepLocationActive(item);
        } else {
            return false;
        }
        return true;
    }

    private void toggleKeepLocationActive(MenuItem item) {
        if (KeepLocationActive.isActive()) {
            KeepLocationActive.stop();
            tasksFragment().showMessage(R.string.debug_keep_location_off);
        } else if (KeepLocationActive.start(this)) {
            tasksFragment().showMessage(R.string.debug_keep_location_on);
        } else {
            tasksFragment().showMessage(R.string.debug_location_permission_needed);
        }
        item.setChecked(KeepLocationActive.isActive());
    }

    private static void setShown(FragmentTransaction transaction, Fragment fragment, boolean shown) {
        if (fragment == null) {
            return;
        }
        if (shown) {
            transaction.show(fragment);
        } else {
            transaction.hide(fragment);
        }
    }

    /**
     * Shows the icons of overflow items (Export / Clear log) and colours the
     * destructive one. MenuBuilder is the toolbar's menu implementation; only its icon flag is used.
     */
    @SuppressLint("RestrictedApi")
    private void setUpOverflowIcons(Menu menu) {
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        int error = MaterialColors.getColor(binding.toolbar, androidx.appcompat.R.attr.colorError);
        int normal = MaterialColors.getColor(binding.toolbar, com.google.android.material.R.attr.colorOnSurfaceVariant);
        tintIcon(menu.findItem(R.id.action_export_log), normal);
        MenuItem clear = menu.findItem(R.id.action_clear_log);
        tintIcon(clear, error);
        SpannableString title = new SpannableString(clear.getTitle());
        title.setSpan(new ForegroundColorSpan(error), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clear.setTitle(title);
    }

    private static void tintIcon(MenuItem item, int color) {
        Drawable icon = item.getIcon();
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(color);
            item.setIcon(icon);
        }
    }

    private ActivityLogFragment activityFragment() {
        return (ActivityLogFragment) getSupportFragmentManager().findFragmentByTag(TAG_ACTIVITY);
    }

    private TasksFragment tasksFragment() {
        return (TasksFragment) getSupportFragmentManager().findFragmentByTag(TAG_TASKS);
    }

    /** Without Play services geofencing can't work, so offer the system fix. */
    private void checkPlayServices() {
        GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
        int result = availability.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS && availability.isUserResolvableError(result)) {
            availability.getErrorDialog(this, result, REQUEST_PLAY_SERVICES).show();
        }
    }
}
