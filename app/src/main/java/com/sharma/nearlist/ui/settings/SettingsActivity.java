package com.sharma.nearlist.ui.settings;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.snackbar.Snackbar;
import com.sharma.nearlist.BuildConfig;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.repo.SettingsRepository;
import com.sharma.nearlist.databinding.ActivitySettingsBinding;
import com.sharma.nearlist.databinding.ItemSettingsPermissionBinding;
import com.sharma.nearlist.notify.NotificationHelper;
import com.sharma.nearlist.util.PermissionHelper;

/**
 * Settings: cooldown, battery-saver awareness, permission status, zone maintenance,
 * demo mode and about. Permission rows and the demo switch are refreshed in onResume.
 */
public class SettingsActivity extends AppCompatActivity {

    /** Error container at about 35 % behind a permission row that is not allowed. */
    private static final int NOT_ALLOWED_ROW_ALPHA = 89;

    private ActivitySettingsBinding binding;
    private SettingsViewModel viewModel;
    private MaterialRadioButton[] cooldownButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        int scrollBottom = binding.scroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, 0, bars.right, 0);
            binding.toolbar.setPadding(0, bars.top, 0, 0);
            binding.scroll.setPadding(0, 0, 0, scrollBottom + bars.bottom);
            return insets;
        });
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        for (View heading : new View[]{binding.sectionReminders, binding.sectionPermissions,
                binding.sectionMaintenance, binding.sectionAbout}) {
            ViewCompat.setAccessibilityHeading(heading, true);
        }

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setUpCooldown();
        binding.batterySwitch.setChecked(viewModel.isBatteryAware());
        binding.batterySwitch.setOnCheckedChangeListener((button, checked) -> viewModel.setBatteryAware(checked));
        binding.batteryRow.setOnClickListener(v -> binding.batterySwitch.toggle());

        binding.demoSwitch.setOnCheckedChangeListener((button, checked) -> viewModel.setDemoEnabled(checked));
        binding.demoRow.setOnClickListener(v -> binding.demoSwitch.toggle());

        binding.reregister.setOnClickListener(v -> viewModel.reregisterAll());
        binding.version.setText(getString(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        binding.licencesRow.setOnClickListener(v -> showLicences());

        viewModel.getZones().observe(this, this::renderZones);
        viewModel.isReregistering().observe(this, busy -> binding.reregister.setEnabled(!Boolean.TRUE.equals(busy)));
        viewModel.getReregisterResult().observe(this, this::showReregisterResult);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPermissions();
        // Demo mode may have expired while the screen was in the background.
        if (binding.demoSwitch.isChecked() != viewModel.isDemoActive()) {
            binding.demoSwitch.setChecked(viewModel.isDemoActive());
        }
        viewModel.refreshZones();
    }

    private void setUpCooldown() {
        cooldownButtons = new MaterialRadioButton[]{
                binding.cooldown1, binding.cooldown4, binding.cooldown8, binding.cooldown24};
        int[] hours = SettingsRepository.COOLDOWN_HOUR_OPTIONS;
        int selected = viewModel.getCooldownHours();
        for (int i = 0; i < cooldownButtons.length; i++) {
            MaterialRadioButton button = cooldownButtons[i];
            int option = hours[i];
            button.setText(getResources().getQuantityString(R.plurals.settings_cooldown_option, option, option));
            button.setChecked(option == selected);
            button.setOnCheckedChangeListener((b, checked) -> {
                if (checked) {
                    selectCooldown(option);
                }
            });
        }
    }

    /** The buttons sit in separate rows (for the "Default" badge), so exclusivity is kept here. */
    private void selectCooldown(int hours) {
        int[] options = SettingsRepository.COOLDOWN_HOUR_OPTIONS;
        for (int i = 0; i < cooldownButtons.length; i++) {
            if (options[i] != hours && cooldownButtons[i].isChecked()) {
                cooldownButtons[i].setChecked(false);
            }
        }
        viewModel.setCooldownHours(hours);
    }

    private void renderPermissions() {
        boolean fine = PermissionHelper.hasFineLocation(this);
        boolean coarse = PermissionHelper.hasCoarseLocation(this);
        int locationStatus = fine ? R.string.settings_allowed_precise
                : coarse ? R.string.settings_allowed_approximate : R.string.settings_not_allowed;
        bindPermission(binding.locationRow, R.drawable.ic_my_location, R.drawable.ic_banner_location_disabled,
                R.string.settings_permission_location, locationStatus, fine || coarse,
                PermissionHelper.appDetailsSettingsIntent(this));

        boolean background = PermissionHelper.hasBackgroundLocation(this);
        int backgroundStatus;
        if (!background) {
            backgroundStatus = R.string.settings_background_not_allowed;
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Below Android 10 there is no separate permission; foreground location covers it.
            backgroundStatus = R.string.settings_allowed_with_location;
        } else {
            backgroundStatus = R.string.settings_allowed;
        }
        bindPermission(binding.backgroundRow, R.drawable.ic_location_on, R.drawable.ic_banner_location_off,
                R.string.settings_permission_background, backgroundStatus, background,
                PermissionHelper.appDetailsSettingsIntent(this));

        boolean notifications = NotificationHelper.canPostNotifications(this);
        bindPermission(binding.notificationsRow, R.drawable.ic_notifications, R.drawable.ic_banner_notifications_off,
                R.string.settings_permission_notifications,
                notifications ? R.string.settings_allowed : R.string.settings_not_allowed, notifications,
                PermissionHelper.appNotificationSettingsIntent(this));
    }

    private void bindPermission(ItemSettingsPermissionBinding row, @DrawableRes int allowedIcon,
                                @DrawableRes int deniedIcon, @StringRes int name, @StringRes int status,
                                boolean allowed, Intent settingsIntent) {
        row.permissionIcon.setImageResource(allowed ? allowedIcon : deniedIcon);
        row.permissionName.setText(name);
        row.permissionStatus.setText(status);
        row.permissionStatus.setTextColor(MaterialColors.getColor(row.getRoot(), allowed
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnErrorContainer));
        row.permissionOk.setVisibility(allowed ? View.VISIBLE : View.GONE);
        row.permissionFix.setVisibility(allowed ? View.GONE : View.VISIBLE);
        int errorContainer = MaterialColors.getColor(row.getRoot(), com.google.android.material.R.attr.colorErrorContainer);
        row.getRoot().setBackgroundColor(allowed ? Color.TRANSPARENT
                : MaterialColors.compositeARGBWithAlpha(errorContainer, NOT_ALLOWED_ROW_ALPHA));
        row.getRoot().setContentDescription(getString(name) + ", " + getString(status));
        View.OnClickListener open = v -> startActivity(settingsIntent);
        row.getRoot().setOnClickListener(open);
        row.permissionFix.setOnClickListener(open);
        row.permissionFix.setBackgroundTintList(ColorStateList.valueOf(
                MaterialColors.getColor(row.getRoot(), androidx.appcompat.R.attr.colorError)));
    }

    private void renderZones(SettingsViewModel.Zones zones) {
        if (zones.registeredAt > 0) {
            CharSequence when = DateUtils.getRelativeDateTimeString(this, zones.registeredAt,
                    DateUtils.DAY_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0);
            binding.zonesStatus.setText(getResources().getQuantityString(R.plurals.settings_zones_status,
                    zones.count, zones.count, when));
        } else {
            binding.zonesStatus.setText(getResources().getQuantityString(R.plurals.settings_zones_status_never,
                    zones.count, zones.count));
        }
    }

    private void showReregisterResult(@Nullable SettingsViewModel.ReregisterResult result) {
        if (result == null) {
            return;
        }
        viewModel.reregisterResultShown();
        String message;
        if (result.places == 0) {
            message = getString(R.string.settings_reregister_none);
        } else if (result.zones == 0) {
            message = getString(R.string.settings_reregister_failed);
        } else {
            message = getResources().getQuantityString(R.plurals.settings_reregistered, result.zones, result.zones);
        }
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private void showLicences() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_licences)
                .setMessage(R.string.settings_licences_body)
                .setPositiveButton(R.string.settings_licences_close, null)
                .show();
    }
}
