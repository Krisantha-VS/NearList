package com.sharma.nearlist.ui.tasks;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.databinding.FragmentTasksBinding;
import com.sharma.nearlist.debug.DebugData;
import com.sharma.nearlist.location.LocationHelper;
import com.sharma.nearlist.ui.common.CategoryUi;
import com.sharma.nearlist.ui.common.StatusBanner;
import com.sharma.nearlist.ui.main.MainActivity;
import com.sharma.nearlist.util.PermissionHelper;

/** Tasks tab (screens 05–07): status banner, category filter, nearest-first list, empty state. */
public class TasksFragment extends Fragment implements TaskAdapter.Listener {

    private FragmentTasksBinding binding;
    private TasksViewModel viewModel;
    private TaskAdapter adapter;
    private CancellationTokenSource locationRequest;

    private final ActivityResultLauncher<IntentSenderRequest> resolveLocationSettings =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> viewModel.refreshBanner());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(TasksViewModel.class);

        adapter = new TaskAdapter(this);
        binding.taskList.setAdapter(adapter);
        binding.taskList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    binding.addTask.shrink();
                } else if (dy < 0) {
                    binding.addTask.extend();
                }
            }
        });

        binding.filterChips.setOnCheckedStateChangeListener((group, checkedIds) ->
                viewModel.setFilter(checkedIds.isEmpty() ? null : categoryForChip(checkedIds.get(0))));
        binding.addTask.setOnClickListener(v -> host().openTaskEditor(AddEditTaskActivity.NEW_TASK));
        binding.savePlace.setOnClickListener(v -> host().showPlacesTab());

        binding.banner.setCloseVisible(true);
        binding.banner.setOnCloseClickListener(v -> viewModel.dismissBanner());

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getBanner().observe(getViewLifecycleOwner(), this::renderBanner);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshBanner();
        refreshLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (locationRequest != null) {
            locationRequest.cancel();
            locationRequest = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onTaskClick(Task task) {
        host().openTaskEditor(task.id);
    }

    @Override
    public void onDoneChanged(Task task, boolean done) {
        viewModel.setDone(task, done);
    }

    @Override
    public void onCompletedHeaderClick() {
        viewModel.toggleCompletedExpanded();
    }

    /** Debug menu "Load sample data": replaces all data with the screen 06 set. */
    public void loadSampleData() {
        DebugData.loadSampleData(requireContext(), () -> showMessage(R.string.sample_data_loaded));
    }

    /** Debug menu "Simulate arrival…": pick a place, then run the real ENTER pipeline for it. */
    public void simulateArrival() {
        DebugData.loadPlaces(requireContext(), places -> {
            if (!isAdded()) {
                return;
            }
            if (places.isEmpty()) {
                showMessage(R.string.debug_no_places);
                return;
            }
            String[] names = new String[places.size()];
            for (int i = 0; i < places.size(); i++) {
                Place place = places.get(i);
                names[i] = place.name + " · " + getString(CategoryUi.label(place.category));
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.debug_simulate_title)
                    .setItems(names, (dialog, which) -> runSimulatedArrival(places.get(which)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    /** Debug menu "Clear all data", after confirmation. */
    public void confirmClearAllData() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.debug_clear_title)
                .setMessage(R.string.debug_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.debug_clear_confirm, (dialog, which) ->
                        DebugData.clearAll(requireContext(),
                                () -> showMessage(R.string.debug_data_cleared)))
                .show();
    }

    /** Short message above the Add task button; ignored once the view is gone. */
    public void showMessage(@StringRes int message) {
        if (binding != null) {
            showMessage(getString(message));
        }
    }

    /** Short message above the Add task button; ignored once the view is gone. */
    public void showMessage(CharSequence message) {
        if (binding == null) {
            return;
        }
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.addTask)
                .show();
    }

    private void runSimulatedArrival(Place place) {
        DebugData.simulateArrival(requireContext(), place.id, event -> {
            if (!isAdded()) {
                return;
            }
            if (event == null) {
                showMessage(R.string.debug_place_gone);
            } else if (ActivityEvent.OUTCOME_NOTIFIED.equals(event.outcome)) {
                showMessage(getString(R.string.debug_simulated_notified, place.name));
            } else {
                showMessage(getString(R.string.debug_simulated_suppressed, place.name,
                        event.reason != null ? event.reason : event.detail));
            }
        });
    }

    private void refreshLocation() {
        if (!PermissionHelper.hasForegroundLocation(requireContext())) {
            return;
        }
        // The callback only touches the ViewModel, which outlives this fragment's views.
        TasksViewModel vm = viewModel;
        locationRequest = LocationHelper.getCurrentLocation(requireContext(),
                Priority.PRIORITY_BALANCED_POWER_ACCURACY, location -> {
                    if (location != null) {
                        vm.setLocation(location);
                    }
                });
    }

    private void render(TasksViewModel.UiState state) {
        binding.emptyState.setVisibility(state.noTasksAtAll ? View.VISIBLE : View.GONE);
        binding.listContent.setVisibility(state.noTasksAtAll ? View.GONE : View.VISIBLE);
        adapter.submitList(state.rows);
        binding.openCount.setText(getResources().getQuantityString(
                R.plurals.tasks_open_count, state.openCount, state.openCount));
        binding.sortLabel.setText(state.locationKnown ? R.string.tasks_sort_nearest : R.string.tasks_sort_added);
        binding.sortIcon.setImageResource(state.locationKnown ? R.drawable.ic_near_me : R.drawable.ic_schedule);
    }

    private void renderBanner(TasksViewModel.Banner banner) {
        StatusBanner view = binding.banner;
        view.setVisibility(banner == TasksViewModel.Banner.NONE ? View.GONE : View.VISIBLE);
        view.setTone(StatusBanner.Tone.ERROR);
        switch (banner) {
            case LOCATION_OFF:
                view.setIcon(R.drawable.ic_banner_location_disabled);
                view.setTitle(R.string.banner_location_off_title);
                view.setBody(R.string.banner_location_off_body);
                view.setAction(R.string.banner_location_off_action, R.drawable.ic_location_pin_filled,
                        v -> turnOnLocation());
                break;
            case BACKGROUND_OFF:
                view.setIcon(R.drawable.ic_banner_location_off);
                view.setTitle(R.string.banner_background_title);
                view.setBody(R.string.banner_background_body);
                view.setAction(R.string.banner_fix, R.drawable.ic_banner_fix,
                        v -> startActivity(PermissionHelper.appDetailsSettingsIntent(requireContext())));
                break;
            case NOTIFICATIONS_OFF:
                view.setIcon(R.drawable.ic_banner_notifications_off);
                view.setTitle(R.string.banner_notifications_title);
                view.setBody(R.string.banner_notifications_body);
                view.setAction(R.string.banner_fix, R.drawable.ic_banner_fix,
                        v -> startActivity(PermissionHelper.appNotificationSettingsIntent(requireContext())));
                break;
            default:
                break;
        }
    }

    private void turnOnLocation() {
        LocationHelper.checkLocationSettings(requireActivity(),
                () -> viewModel.refreshBanner(),
                resolvable -> resolveLocationSettings.launch(
                        new IntentSenderRequest.Builder(resolvable.getResolution()).build()),
                () -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
    }

    private static Category categoryForChip(int chipId) {
        if (chipId == R.id.chipGrocery) {
            return Category.GROCERY;
        } else if (chipId == R.id.chipFuel) {
            return Category.FUEL;
        } else if (chipId == R.id.chipPharmacy) {
            return Category.PHARMACY;
        } else if (chipId == R.id.chipOther) {
            return Category.OTHER;
        }
        return null;
    }

    private MainActivity host() {
        return (MainActivity) requireActivity();
    }
}
