package com.sharma.nearlist.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.databinding.FragmentActivityBinding;
import com.sharma.nearlist.ui.main.MainActivity;

import java.util.List;

/** Activity tab: outcome filter, events grouped by day, export and clear. */
public class ActivityLogFragment extends Fragment {

    private FragmentActivityBinding binding;
    private ActivityViewModel viewModel;
    private ActivityAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentActivityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ActivityViewModel.class);
        adapter = new ActivityAdapter();
        binding.eventList.setAdapter(adapter);

        binding.filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int checked = checkedIds.isEmpty() ? R.id.chipAll : checkedIds.get(0);
            if (checked == R.id.chipNotified) {
                viewModel.setFilter(ActivityEvent.OUTCOME_NOTIFIED);
            } else if (checked == R.id.chipSuppressed) {
                viewModel.setFilter(ActivityEvent.OUTCOME_SUPPRESSED);
            } else {
                viewModel.setFilter(null);
            }
        });

        viewModel.getRows().observe(getViewLifecycleOwner(), this::renderRows);
        viewModel.getTotalCount().observe(getViewLifecycleOwner(), this::renderTotal);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** Overflow "Export log (CSV)": shares the whole log as text/csv in EXTRA_TEXT. */
    public void exportLog() {
        viewModel.exportCsv(csv -> {
            if (!isAdded()) {
                return;
            }
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.activity_export_subject))
                    .putExtra(Intent.EXTRA_TEXT, csv);
            startActivity(Intent.createChooser(send, getString(R.string.activity_export_chooser)));
        });
    }

    /** Overflow "Clear log", after confirmation. */
    public void confirmClearLog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.activity_clear_title)
                .setMessage(R.string.activity_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.activity_clear_confirm, (dialog, which) -> viewModel.clear())
                .show();
    }

    private void renderRows(List<ActivityAdapter.Row> rows) {
        adapter.submitList(rows);
        renderFilterEmpty();
    }

    private void renderTotal(Integer total) {
        boolean empty = total == null || total == 0;
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.listContent.setVisibility(empty ? View.GONE : View.VISIBLE);
        renderFilterEmpty();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setActivityLogHasEvents(!empty);
        }
    }

    /** "No notified events yet" when the filter matches nothing but the log has entries. */
    private void renderFilterEmpty() {
        List<ActivityAdapter.Row> rows = viewModel.getRows().getValue();
        String filter = viewModel.getFilter().getValue();
        boolean show = rows != null && rows.isEmpty() && filter != null;
        binding.filterEmpty.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            binding.filterEmpty.setText(ActivityEvent.OUTCOME_NOTIFIED.equals(filter)
                    ? R.string.activity_none_notified : R.string.activity_none_suppressed);
        }
    }
}
