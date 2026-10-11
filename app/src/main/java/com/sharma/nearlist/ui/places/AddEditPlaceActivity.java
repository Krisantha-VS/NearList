package com.sharma.nearlist.ui.places;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.location.LocationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;
import com.google.android.material.snackbar.Snackbar;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.data.repo.PlaceRepository;
import com.sharma.nearlist.databinding.ActivityAddEditPlaceBinding;
import com.sharma.nearlist.location.LocationHelper;
import com.sharma.nearlist.ui.common.CategoryUi;
import com.sharma.nearlist.ui.common.StatusBanner;
import com.sharma.nearlist.util.Formatters;
import com.sharma.nearlist.util.PermissionHelper;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * New / edit place form (screens 14–17). The position comes from a GPS fix or typed coordinates;
 * there is no map. Form state lives in {@link PlaceEditViewModel}.
 */
public class AddEditPlaceActivity extends AppCompatActivity {

    /** Pass to {@link #newIntent} or as the place id for a new place. */
    public static final long NEW_PLACE = 0L;
    /** Id of the place to edit; absent for a new place. */
    public static final String EXTRA_PLACE_ID = "com.sharma.nearlist.extra.PLACE_ID";
    /** {@link Category#key()} to pre-select for a new place (task form "Add a place", Places "Add"). */
    public static final String EXTRA_CATEGORY = "com.sharma.nearlist.extra.CATEGORY";

    private static final long PULSE_MS = 1_400L;
    private static final long JUST_NOW_MS = 60_000L;
    private static final float STATUS_ICON_DP = 14f;

    private ActivityAddEditPlaceBinding binding;
    private PlaceEditViewModel viewModel;
    private final Map<Category, Chip> chips = new EnumMap<>(Category.class);
    private AnimatorSet pulse;
    /** True while fields are filled from the ViewModel, so the watchers don't treat it as typing. */
    private boolean populating;
    private boolean nameEdited;
    private boolean radiusShown;

    private final ActivityResultLauncher<String[]> requestLocationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (PermissionHelper.hasForegroundLocation(this)) {
                    useCurrentLocation();
                } else {
                    viewModel.expandManual();
                    viewModel.showMessage(R.string.place_edit_permission_needed);
                }
            });

    private final ActivityResultLauncher<IntentSenderRequest> resolveLocationSettings =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    viewModel.clearLocationOff();
                    viewModel.startAcquiring();
                }
            });

    /** New place, optionally with a pre-selected category. */
    public static Intent newIntent(Context context, @Nullable Category category) {
        Intent intent = new Intent(context, AddEditPlaceActivity.class);
        if (category != null) {
            intent.putExtra(EXTRA_CATEGORY, category.key());
        }
        return intent;
    }

    public static Intent editIntent(Context context, long placeId) {
        return new Intent(context, AddEditPlaceActivity.class).putExtra(EXTRA_PLACE_ID, placeId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddEditPlaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        viewModel = new ViewModelProvider(this).get(PlaceEditViewModel.class);
        viewModel.start(getIntent().getLongExtra(EXTRA_PLACE_ID, NEW_PLACE), presetCategory());

        binding.toolbar.setTitle(viewModel.isEditing() ? R.string.place_edit_title_edit : R.string.place_edit_title_new);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                save();
                return true;
            }
            return false;
        });

        setUpLocation();
        setUpManual();
        setUpName();
        setUpCategoryChips();
        setUpRadius();
        binding.deleteButton.setVisibility(viewModel.isEditing() ? View.VISIBLE : View.GONE);
        binding.deleteButton.setOnClickListener(v -> confirmDelete());

        viewModel.getLoadState().observe(this, this::renderLoadState);
        viewModel.getCaptureState().observe(this, state -> renderLocation());
        viewModel.getCoordinates().observe(this, coordinates -> {
            fillManualFields(coordinates);
            renderLocation();
            updateSaveEnabled();
        });
        viewModel.getManualExpanded().observe(this, this::renderManualExpanded);
        viewModel.getName().observe(this, this::renderName);
        viewModel.getCategory().observe(this, this::renderCategory);
        viewModel.getRadius().observe(this, this::renderRadius);
        viewModel.getMessage().observe(this, this::showMessage);
        viewModel.getSaveResult().observe(this, this::onSaveResult);

        // A new place starts by locating the user, as in screen 15.
        if (savedInstanceState == null && !viewModel.isEditing()) {
            useCurrentLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from system settings with location switched on: carry on locating.
        if (viewModel.getCaptureState().getValue() == PlaceEditViewModel.CaptureState.LOCATION_OFF
                && isLocationEnabled()) {
            viewModel.clearLocationOff();
            viewModel.startAcquiring();
        }
    }

    @Override
    protected void onDestroy() {
        stopPulse();
        super.onDestroy();
    }

    @Nullable
    private Category presetCategory() {
        try {
            return Category.fromKey(getIntent().getStringExtra(EXTRA_CATEGORY));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void applyInsets() {
        int scrollBottom = binding.scroll.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, 0, bars.right, 0);
            binding.toolbar.setPadding(0, bars.top, 0, 0);
            binding.scroll.setPadding(0, 0, 0, scrollBottom + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
    }

    private void setUpLocation() {
        StatusBanner banner = binding.locationBanner;
        banner.setTone(StatusBanner.Tone.ERROR);
        banner.setIcon(R.drawable.ic_banner_location_disabled);
        banner.setTitle(R.string.place_edit_location_off_title);
        banner.setBody(R.string.place_edit_location_off_body);
        banner.setAction(R.string.place_edit_location_off_action, R.drawable.ic_location_pin_filled,
                v -> turnOnLocation());
        binding.useLocation.setOnClickListener(v -> useCurrentLocation());
        binding.refreshLocation.setOnClickListener(v -> useCurrentLocation());
    }

    private void setUpManual() {
        binding.manualToggle.setOnClickListener(v -> viewModel.toggleManual());
        // The fields don't save their own state (saveEnabled=false): restoring it would look like
        // typing and turn a GPS fix into manual coordinates. The ViewModel keeps the texts instead.
        setManualFields(viewModel.getManualLatitudeText(), viewModel.getManualLongitudeText());
        TextWatcher watcher = new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (populating) {
                    return;
                }
                String lat = String.valueOf(binding.latitudeInput.getText());
                String lng = String.valueOf(binding.longitudeInput.getText());
                viewModel.setManualText(lat, lng);
                binding.latitudeLayout.setError(fieldError(lat, 90, R.string.place_edit_latitude_error));
                binding.longitudeLayout.setError(fieldError(lng, 180, R.string.place_edit_longitude_error));
            }
        };
        binding.latitudeInput.addTextChangedListener(watcher);
        binding.longitudeInput.addTextChangedListener(watcher);
    }

    /** Error only for a non-blank value that isn't a coordinate in range. */
    @Nullable
    private String fieldError(String text, double limit, int message) {
        boolean invalid = !text.trim().isEmpty() && PlaceEditViewModel.parseCoordinate(text, limit) == null;
        return invalid ? getString(message) : null;
    }

    private void setUpName() {
        binding.nameInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!populating) {
                    nameEdited = true;
                }
                viewModel.setName(s.toString());
            }
        });
    }

    private void setUpCategoryChips() {
        chips.put(Category.GROCERY, binding.chipGrocery);
        chips.put(Category.FUEL, binding.chipFuel);
        chips.put(Category.PHARMACY, binding.chipPharmacy);
        chips.put(Category.OTHER, binding.chipOther);
        for (Map.Entry<Category, Chip> entry : chips.entrySet()) {
            CategoryUi.styleChoiceChip(entry.getValue(), entry.getKey());
        }
        binding.categoryChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            Category selected = null;
            for (Map.Entry<Category, Chip> entry : chips.entrySet()) {
                if (checkedIds.contains(entry.getValue().getId())) {
                    selected = entry.getKey();
                }
            }
            viewModel.setCategory(selected);
            // Once chosen, a category can be changed but not cleared.
            binding.categoryChips.setSelectionRequired(selected != null);
        });
    }

    private void setUpRadius() {
        binding.radiusSlider.setLabelFormatter(value -> Formatters.radius(Math.round(value)));
        binding.radiusSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                viewModel.setRadius(Math.round(value));
            }
        });
        // Tick labels line up with the track, which is inset by the slider's side padding.
        binding.radiusSlider.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int side = binding.radiusSlider.getTrackSidePadding();
            if (binding.radiusTicks.getPaddingStart() != side) {
                binding.radiusTicks.setPaddingRelative(side, 0, side, 0);
            }
        });
    }

    private void renderLoadState(PlaceEditViewModel.LoadState state) {
        if (state == PlaceEditViewModel.LoadState.NOT_FOUND) {
            Toast.makeText(this, R.string.place_edit_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        updateSaveEnabled();
    }

    private void renderLocation() {
        PlaceEditViewModel.CaptureState state = viewModel.getCaptureState().getValue();
        PlaceEditViewModel.Coordinates coords = viewModel.getCoordinates().getValue();
        boolean acquiring = state == PlaceEditViewModel.CaptureState.ACQUIRING;
        boolean locationOff = state == PlaceEditViewModel.CaptureState.LOCATION_OFF;

        binding.locationBanner.setVisibility(locationOff ? View.VISIBLE : View.GONE);
        binding.useLocation.setVisibility(locationOff ? View.GONE : View.VISIBLE);
        binding.useLocation.setEnabled(!acquiring);
        if (acquiring) {
            binding.useLocation.setText(R.string.place_edit_getting_location);
            binding.useLocation.setIcon(progressIcon());
        } else {
            binding.useLocation.setText(viewModel.isEditing()
                    ? R.string.place_edit_update_location : R.string.place_edit_use_location);
            binding.useLocation.setIconResource(R.drawable.ic_my_location);
        }

        binding.acquiringCard.setVisibility(acquiring ? View.VISIBLE : View.GONE);
        if (acquiring) {
            startPulse();
        } else {
            stopPulse();
        }

        boolean showCard = coords != null && !acquiring;
        binding.coordinateCard.setVisibility(showCard ? View.VISIBLE : View.GONE);
        if (showCard) {
            renderCoordinateCard(coords);
        }
    }

    private void renderCoordinateCard(PlaceEditViewModel.Coordinates coords) {
        binding.coordinatesText.setText(getString(R.string.place_edit_coordinates,
                Formatters.coordinate(coords.latitude), Formatters.coordinate(coords.longitude)));
        boolean gps = coords.source == PlaceEditViewModel.Source.GPS;
        Integer accuracy = coords.accuracyM != null ? Math.round(coords.accuracyM) : null;
        String status;
        @DrawableRes int icon;
        switch (coords.source) {
            case GPS:
                icon = R.drawable.ic_gps_fixed;
                status = fixStatus(accuracy, coords.time);
                break;
            case MANUAL:
                icon = R.drawable.ic_edit_location;
                status = getString(R.string.place_edit_entered_manually);
                break;
            default:
                icon = R.drawable.ic_nav_activity;
                String date = DateUtils.formatDateTime(this, coords.time,
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_MONTH);
                status = accuracy != null
                        ? getString(R.string.place_edit_saved_on_accuracy, date, accuracy)
                        : getString(R.string.place_edit_saved_on, date);
                break;
        }
        binding.coordinatesStatus.setText(status);
        setStatusIcon(icon);
        binding.lowAccuracy.setVisibility(gps && coords.accuracyM != null
                && coords.accuracyM > PlaceEditViewModel.LOW_ACCURACY_M ? View.VISIBLE : View.GONE);
        binding.refreshLocation.setVisibility(gps ? View.VISIBLE : View.GONE);
        // A fresh fix is outlined in primary (screen 14); saved or typed positions are neutral (17).
        binding.coordinateCard.setStrokeColor(MaterialColors.getColor(binding.coordinateCard, gps
                ? androidx.appcompat.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOutlineVariant));
    }

    private String fixStatus(@Nullable Integer accuracy, long time) {
        long now = System.currentTimeMillis();
        boolean justNow = now - time < JUST_NOW_MS;
        CharSequence ago = DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS);
        if (accuracy == null) {
            return justNow ? getString(R.string.place_edit_fix_just_now) : getString(R.string.place_edit_fix_ago, ago);
        }
        return justNow ? getString(R.string.place_edit_accuracy_just_now, accuracy)
                : getString(R.string.place_edit_accuracy_ago, accuracy, ago);
    }

    private void setStatusIcon(@DrawableRes int icon) {
        Drawable drawable = ContextCompat.getDrawable(this, icon);
        if (drawable == null) {
            return;
        }
        drawable = drawable.mutate();
        int size = Math.round(STATUS_ICON_DP * getResources().getDisplayMetrics().density);
        drawable.setBounds(0, 0, size, size);
        drawable.setTint(MaterialColors.getColor(binding.coordinatesStatus,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        TextViewCompat.setCompoundDrawablesRelative(binding.coordinatesStatus, drawable, null, null, null);
    }

    /** GPS and saved positions are copied into the manual fields; typed values are left alone. */
    private void fillManualFields(@Nullable PlaceEditViewModel.Coordinates coords) {
        if (coords == null || coords.source == PlaceEditViewModel.Source.MANUAL) {
            return;
        }
        String lat = String.format(Locale.US, "%.6f", coords.latitude);
        String lng = String.format(Locale.US, "%.6f", coords.longitude);
        viewModel.setManualTextFromFix(lat, lng);
        setManualFields(lat, lng);
        binding.latitudeLayout.setError(null);
        binding.longitudeLayout.setError(null);
    }

    private void setManualFields(String lat, String lng) {
        populating = true;
        if (!lat.equals(String.valueOf(binding.latitudeInput.getText()))) {
            binding.latitudeInput.setText(lat);
        }
        if (!lng.equals(String.valueOf(binding.longitudeInput.getText()))) {
            binding.longitudeInput.setText(lng);
        }
        populating = false;
    }

    private void renderManualExpanded(boolean expanded) {
        binding.manualSection.setVisibility(expanded ? View.VISIBLE : View.GONE);
        binding.manualExpandIcon.setImageResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
    }

    private void renderName(String name) {
        if (!name.equals(String.valueOf(binding.nameInput.getText()))) {
            populating = true;
            binding.nameInput.setText(name);
            binding.nameInput.setSelection(name.length());
            populating = false;
        }
        boolean blank = name.trim().isEmpty();
        binding.nameLayout.setError(blank && nameEdited ? getString(R.string.place_edit_name_error) : null);
        updateSaveEnabled();
    }

    private void renderCategory(@Nullable Category category) {
        if (category == null) {
            binding.categoryChips.clearCheck();
        } else {
            Chip chip = chips.get(category);
            if (chip != null && !chip.isChecked()) {
                chip.setChecked(true);
            }
        }
        updateSaveEnabled();
    }

    private void renderRadius(int meters) {
        if (Math.round(binding.radiusSlider.getValue()) != meters) {
            binding.radiusSlider.setValue(meters);
        }
        binding.radiusSlider.setContentDescription(
                getString(R.string.place_edit_radius_description, Formatters.radius(meters)));
        binding.radiusPreview.setRadius(meters, radiusShown);
        radiusShown = true;
    }

    private void showMessage(@Nullable Integer text) {
        if (text == null) {
            return;
        }
        Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG).show();
        viewModel.messageShown();
    }

    private void onSaveResult(@Nullable PlaceEditViewModel.SaveResult result) {
        if (result == null) {
            return;
        }
        viewModel.saveResultHandled();
        if (result == PlaceEditViewModel.SaveResult.SAVED) {
            finish();
        } else {
            Snackbar.make(binding.getRoot(),
                    getString(R.string.place_edit_limit_reached, PlaceRepository.MAX_PLACES),
                    Snackbar.LENGTH_LONG).show();
            updateSaveEnabled();
        }
    }

    private void updateSaveEnabled() {
        binding.toolbar.getMenu().findItem(R.id.action_save).setEnabled(viewModel.canSave());
    }

    private void save() {
        if (!viewModel.canSave()) {
            nameEdited = true;
            renderName(String.valueOf(binding.nameInput.getText()));
            return;
        }
        viewModel.save();
        updateSaveEnabled();
    }

    private void confirmDelete() {
        String name = String.valueOf(binding.nameInput.getText()).trim();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.places_delete_title, name))
                .setMessage(R.string.places_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.places_delete_confirm, (dialog, which) -> {
                    viewModel.delete();
                    finish();
                })
                .show();
    }

    /** Permission → location services → fix. Each missing piece leads to its own prompt. */
    private void useCurrentLocation() {
        if (!PermissionHelper.hasForegroundLocation(this)) {
            requestLocationPermission.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        if (!isLocationEnabled()) {
            viewModel.setLocationOff();
            return;
        }
        viewModel.clearLocationOff();
        viewModel.startAcquiring();
    }

    private void turnOnLocation() {
        LocationHelper.checkLocationSettings(this,
                () -> {
                    viewModel.clearLocationOff();
                    viewModel.startAcquiring();
                },
                resolvable -> resolveLocationSettings.launch(
                        new IntentSenderRequest.Builder(resolvable.getResolution()).build()),
                () -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
    }

    private boolean isLocationEnabled() {
        LocationManager manager = getSystemService(LocationManager.class);
        return manager != null && LocationManagerCompat.isLocationEnabled(manager);
    }

    private Drawable progressIcon() {
        CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(this, null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
        spec.indicatorColors = new int[]{MaterialColors.getColor(binding.useLocation,
                com.google.android.material.R.attr.colorOnSecondaryContainer)};
        return IndeterminateDrawable.createCircularDrawable(this, spec);
    }

    /** Expanding, fading circle behind the satellite icon (screen 15). */
    private void startPulse() {
        if (pulse != null) {
            return;
        }
        View view = binding.acquiringPulse;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.6f, 1.25f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.6f, 1.25f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.9f, 0f);
        for (ObjectAnimator animator : new ObjectAnimator[]{scaleX, scaleY, alpha}) {
            animator.setRepeatCount(ValueAnimator.INFINITE);
        }
        pulse = new AnimatorSet();
        pulse.playTogether(scaleX, scaleY, alpha);
        pulse.setDuration(PULSE_MS);
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    /** TextWatcher with only {@code afterTextChanged} to implement. */
    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
