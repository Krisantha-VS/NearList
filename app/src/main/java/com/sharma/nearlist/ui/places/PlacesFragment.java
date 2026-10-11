package com.sharma.nearlist.ui.places;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.databinding.FragmentPlacesBinding;

import java.util.List;

/** Places tab (screens 11–13): zones summary, places grouped by category, empty state. */
public class PlacesFragment extends Fragment implements PlaceAdapter.Listener {

    private FragmentPlacesBinding binding;
    private PlacesViewModel viewModel;
    private PlaceAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlacesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(PlacesViewModel.class);
        adapter = new PlaceAdapter(this);
        binding.placeList.setAdapter(adapter);
        binding.placeList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    binding.addPlace.shrink();
                } else if (dy < 0) {
                    binding.addPlace.extend();
                }
            }
        });
        binding.addPlace.setOnClickListener(v -> openPlaceForm(null));
        binding.emptyAddPlace.setOnClickListener(v -> openPlaceForm(null));

        viewModel.getRows().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshZones();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onPlaceClick(Place place) {
        startActivity(AddEditPlaceActivity.editIntent(requireContext(), place.id));
    }

    @Override
    public void onPlaceMenu(Place place, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.inflate(R.menu.menu_place_item);
        popup.setForceShowIcon(true);
        tintDeleteItem(popup.getMenu().findItem(R.id.action_delete_place));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit_place) {
                onPlaceClick(place);
                return true;
            } else if (item.getItemId() == R.id.action_delete_place) {
                confirmDelete(place);
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onAddPlace(Category category) {
        openPlaceForm(category);
    }

    private void render(List<PlaceAdapter.Row> rows) {
        boolean empty = rows.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.placeList.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.addPlace.setVisibility(empty ? View.GONE : View.VISIBLE);
        adapter.submitList(rows);
    }

    private void openPlaceForm(@Nullable Category category) {
        startActivity(AddEditPlaceActivity.newIntent(requireContext(), category));
    }

    private void confirmDelete(Place place) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.places_delete_title, place.name))
                .setMessage(R.string.places_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.places_delete_confirm, (dialog, which) -> viewModel.delete(place))
                .show();
    }

    /** Destructive item in the error colour, as in screen 12. */
    private void tintDeleteItem(MenuItem item) {
        int error = MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorError);
        SpannableString title = new SpannableString(item.getTitle());
        title.setSpan(new ForegroundColorSpan(error), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        item.setTitle(title);
        Drawable icon = item.getIcon();
        if (icon != null) {
            icon = icon.mutate();
            icon.setTintList(ColorStateList.valueOf(error));
            item.setIcon(icon);
        }
    }
}
