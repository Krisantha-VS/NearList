package com.sharma.nearlist.ui.tasks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.databinding.ActivityAddEditTaskBinding;
import com.sharma.nearlist.ui.common.CategoryUi;
import com.sharma.nearlist.ui.common.StatusBanner;
import com.sharma.nearlist.ui.places.AddEditPlaceActivity;
import com.sharma.nearlist.util.Formatters;

import java.util.EnumMap;
import java.util.Map;

/**
 * New / edit task form. Form state lives in {@link TaskEditViewModel}, so it
 * survives rotation; the views only mirror it.
 */
public class AddEditTaskActivity extends AppCompatActivity {

    /** Pass to {@link #newIntent(Context, long)} to create a task. */
    public static final long NEW_TASK = 0L;
    /** Id of the task to edit; absent for a new task. */
    public static final String EXTRA_TASK_ID = "com.sharma.nearlist.extra.TASK_ID";

    private static final String TAG_START_PICKER = "start_picker";
    private static final String TAG_END_PICKER = "end_picker";

    private ActivityAddEditTaskBinding binding;
    private TaskEditViewModel viewModel;
    private final Map<Category, Chip> chips = new EnumMap<>(Category.class);
    /** True while the views are filled from the ViewModel, so the watchers don't count it as typing. */
    private boolean populating;
    private boolean titleEdited;

    public static Intent newIntent(Context context, long taskId) {
        Intent intent = new Intent(context, AddEditTaskActivity.class);
        if (taskId != NEW_TASK) {
            intent.putExtra(EXTRA_TASK_ID, taskId);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAddEditTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        viewModel = new ViewModelProvider(this).get(TaskEditViewModel.class);
        viewModel.start(getIntent().getLongExtra(EXTRA_TASK_ID, NEW_TASK));

        binding.toolbar.setTitle(viewModel.isEditing() ? R.string.task_edit_title_edit : R.string.task_edit_title_new);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                save();
                return true;
            }
            return false;
        });

        setUpTitle();
        setUpCategoryChips();
        setUpWindow();
        setUpNotes();
        binding.noPlaceBanner.setTone(StatusBanner.Tone.INFO);
        binding.noPlaceBanner.setIcon(R.drawable.ic_banner_location_off);
        binding.noPlaceBanner.setBody(R.string.task_edit_no_place_body);
        binding.noPlaceBanner.setAction(R.string.task_edit_add_place, R.drawable.ic_add_location, v -> addPlace());
        binding.deleteButton.setVisibility(viewModel.isEditing() ? View.VISIBLE : View.GONE);
        binding.deleteButton.setOnClickListener(v -> confirmDelete());

        viewModel.getLoadState().observe(this, this::renderLoadState);
        viewModel.getTitle().observe(this, this::renderTitle);
        viewModel.getCategory().observe(this, this::renderCategory);
        viewModel.getPlaceCount().observe(this, count -> renderPlaces());
        viewModel.getWindowEnabled().observe(this, enabled -> renderWindow());
        viewModel.getWindowStartMin().observe(this, minutes -> renderWindow());
        viewModel.getWindowEndMin().observe(this, minutes -> renderWindow());
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

    private void setUpTitle() {
        binding.titleInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!populating) {
                    titleEdited = true;
                }
                viewModel.setTitle(s.toString());
            }
        });
    }

    private void setUpNotes() {
        binding.notesInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setNotes(s.toString());
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
            for (Map.Entry<Category, Chip> entry : chips.entrySet()) {
                if (checkedIds.contains(entry.getValue().getId())) {
                    viewModel.setCategory(entry.getKey());
                }
            }
        });
    }

    private void setUpWindow() {
        binding.windowSwitch.setOnCheckedChangeListener((button, checked) -> viewModel.setWindowEnabled(checked));
        binding.windowRow.setOnClickListener(v -> binding.windowSwitch.toggle());
        binding.startInput.setOnClickListener(v -> showTimePicker(TAG_START_PICKER));
        binding.startLayout.setEndIconOnClickListener(v -> showTimePicker(TAG_START_PICKER));
        binding.endInput.setOnClickListener(v -> showTimePicker(TAG_END_PICKER));
        binding.endLayout.setEndIconOnClickListener(v -> showTimePicker(TAG_END_PICKER));
        // A picker left open across rotation is restored by the FragmentManager without listeners.
        reattachPicker(TAG_START_PICKER);
        reattachPicker(TAG_END_PICKER);
    }

    private void renderLoadState(TaskEditViewModel.LoadState state) {
        if (state == TaskEditViewModel.LoadState.NOT_FOUND) {
            Toast.makeText(this, R.string.task_edit_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (state == TaskEditViewModel.LoadState.READY) {
            String notes = viewModel.getNotes();
            if (!notes.equals(String.valueOf(binding.notesInput.getText()))) {
                binding.notesInput.setText(notes);
            }
        }
        updateSaveEnabled();
    }

    private void renderTitle(String title) {
        if (!title.equals(String.valueOf(binding.titleInput.getText()))) {
            populating = true;
            binding.titleInput.setText(title);
            binding.titleInput.setSelection(title.length());
            populating = false;
        }
        boolean blank = title.trim().isEmpty();
        binding.titleLayout.setError(blank && titleEdited ? getString(R.string.task_edit_title_error) : null);
        updateSaveEnabled();
    }

    private void renderCategory(Category category) {
        Chip chip = chips.get(category);
        if (chip != null && !chip.isChecked()) {
            chip.setChecked(true);
        }
        binding.placesHelperIcon.setImageTintList(ContextCompat.getColorStateList(this, CategoryUi.accent(category)));
        renderPlaces();
    }

    /** Helper row when the category has places, the no-place banner when it has none. */
    private void renderPlaces() {
        Integer count = viewModel.getPlaceCount().getValue();
        Category category = viewModel.getCategory().getValue();
        if (count == null || category == null) {
            binding.placesHelper.setVisibility(View.GONE);
            binding.noPlaceBanner.setVisibility(View.GONE);
            return;
        }
        String label = getString(CategoryUi.label(category));
        binding.placesHelper.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.noPlaceBanner.setVisibility(count > 0 ? View.GONE : View.VISIBLE);
        if (count > 0) {
            binding.placesHelperText.setText(getResources().getQuantityString(
                    R.plurals.task_edit_places_helper, count, count, label));
        } else {
            binding.noPlaceBanner.setTitle(getString(R.string.task_edit_no_place_title, label));
        }
    }

    private void renderWindow() {
        boolean enabled = Boolean.TRUE.equals(viewModel.getWindowEnabled().getValue());
        if (binding.windowSwitch.isChecked() != enabled) {
            binding.windowSwitch.setChecked(enabled);
        }
        binding.windowSubtitle.setText(enabled ? R.string.task_edit_window_on : R.string.task_edit_window_off);
        binding.windowTimes.setVisibility(enabled ? View.VISIBLE : View.GONE);

        int start = minutes(viewModel.getWindowStartMin().getValue(), TaskEditViewModel.DEFAULT_WINDOW_START_MIN);
        int end = minutes(viewModel.getWindowEndMin().getValue(), TaskEditViewModel.DEFAULT_WINDOW_END_MIN);
        binding.startInput.setText(Formatters.timeOfDay(this, start));
        binding.endInput.setText(Formatters.timeOfDay(this, end));

        @StringRes int hint = 0;
        if (enabled && end < start) {
            hint = R.string.task_edit_window_overnight;
        } else if (enabled && end == start) {
            hint = R.string.task_edit_window_all_day;
        }
        binding.windowHint.setVisibility(hint != 0 ? View.VISIBLE : View.GONE);
        if (hint != 0) {
            binding.windowHint.setText(hint);
        }
    }

    private void updateSaveEnabled() {
        binding.toolbar.getMenu().findItem(R.id.action_save).setEnabled(viewModel.canSave());
    }

    private void save() {
        if (!viewModel.canSave()) {
            titleEdited = true;
            binding.titleLayout.setError(getString(R.string.task_edit_title_error));
            return;
        }
        viewModel.save();
        finish();
    }

    private void confirmDelete() {
        String title = String.valueOf(binding.titleInput.getText()).trim();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.task_edit_delete_title)
                .setMessage(getString(R.string.task_edit_delete_message, title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.task_edit_delete_confirm, (dialog, which) -> {
                    viewModel.delete();
                    finish();
                })
                .show();
    }

    private void addPlace() {
        startActivity(AddEditPlaceActivity.newIntent(this, viewModel.getCategory().getValue()));
    }

    private void showTimePicker(String tag) {
        if (getSupportFragmentManager().findFragmentByTag(tag) != null) {
            return;
        }
        boolean start = TAG_START_PICKER.equals(tag);
        int minutes = start
                ? minutes(viewModel.getWindowStartMin().getValue(), TaskEditViewModel.DEFAULT_WINDOW_START_MIN)
                : minutes(viewModel.getWindowEndMin().getValue(), TaskEditViewModel.DEFAULT_WINDOW_END_MIN);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(minutes / 60)
                .setMinute(minutes % 60)
                .setTitleText(start ? R.string.task_edit_window_pick_start : R.string.task_edit_window_pick_end)
                .build();
        addPickerListener(picker, start);
        picker.show(getSupportFragmentManager(), tag);
    }

    private void reattachPicker(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof MaterialTimePicker) {
            addPickerListener((MaterialTimePicker) fragment, TAG_START_PICKER.equals(tag));
        }
    }

    private void addPickerListener(@NonNull MaterialTimePicker picker, boolean start) {
        picker.addOnPositiveButtonClickListener(v -> {
            int minutes = picker.getHour() * 60 + picker.getMinute();
            if (start) {
                viewModel.setWindowStartMin(minutes);
            } else {
                viewModel.setWindowEndMin(minutes);
            }
        });
    }

    private static int minutes(Integer value, int fallback) {
        return value != null ? value : fallback;
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
