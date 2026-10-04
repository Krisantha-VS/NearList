package com.sharma.nearlist.ui.places;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.data.repo.PlaceRepository;
import com.sharma.nearlist.databinding.ItemPlaceBinding;
import com.sharma.nearlist.databinding.ItemPlaceEmptyCategoryBinding;
import com.sharma.nearlist.databinding.ItemPlaceHeaderBinding;
import com.sharma.nearlist.databinding.ItemPlaceSummaryBinding;
import com.sharma.nearlist.ui.common.CategoryUi;
import com.sharma.nearlist.util.Formatters;

/** Places list: zones summary, category headers, place rows and empty-category rows. */
public class PlaceAdapter extends ListAdapter<PlaceAdapter.Row, RecyclerView.ViewHolder> {

    public interface Listener {
        void onPlaceClick(Place place);

        /** Overflow button; the fragment shows the Edit / Delete menu anchored to it. */
        void onPlaceMenu(Place place, View anchor);

        void onAddPlace(Category category);
    }

    /** One list row. Immutable. */
    public static final class Row {
        static final int TYPE_SUMMARY = 0;
        static final int TYPE_HEADER = 1;
        static final int TYPE_PLACE = 2;
        static final int TYPE_EMPTY_CATEGORY = 3;

        final int type;
        final Category category;
        final Place place;
        /** Summary: places; header: places in the category; empty row: open tasks waiting. */
        final int count;
        /** Summary only. */
        final int zones;

        private Row(int type, Category category, Place place, int count, int zones) {
            this.type = type;
            this.category = category;
            this.place = place;
            this.count = count;
            this.zones = zones;
        }

        static Row summary(int places, int zones) {
            return new Row(TYPE_SUMMARY, null, null, places, zones);
        }

        static Row header(Category category, int places) {
            return new Row(TYPE_HEADER, category, null, places, 0);
        }

        static Row place(Place place) {
            return new Row(TYPE_PLACE, place.category, place, 0, 0);
        }

        static Row emptyCategory(Category category, int tasksWaiting) {
            return new Row(TYPE_EMPTY_CATEGORY, category, null, tasksWaiting, 0);
        }

        boolean sameItem(Row other) {
            if (type != other.type) {
                return false;
            }
            switch (type) {
                case TYPE_PLACE:
                    return place.id == other.place.id;
                case TYPE_SUMMARY:
                    return true;
                default:
                    return category == other.category;
            }
        }

        boolean sameContent(Row other) {
            if (type != TYPE_PLACE) {
                return count == other.count && zones == other.zones;
            }
            Place a = place;
            Place b = other.place;
            return a.name.equals(b.name) && a.category == b.category && a.latitude == b.latitude
                    && a.longitude == b.longitude && a.radiusM == b.radiusM;
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Row oldItem, @NonNull Row newItem) {
            return oldItem.sameItem(newItem);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Row oldItem, @NonNull Row newItem) {
            return oldItem.sameContent(newItem);
        }
    };

    private final Listener listener;

    public PlaceAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case Row.TYPE_SUMMARY:
                return new SummaryHolder(ItemPlaceSummaryBinding.inflate(inflater, parent, false));
            case Row.TYPE_HEADER:
                return new HeaderHolder(ItemPlaceHeaderBinding.inflate(inflater, parent, false));
            case Row.TYPE_EMPTY_CATEGORY:
                return new EmptyCategoryHolder(ItemPlaceEmptyCategoryBinding.inflate(inflater, parent, false));
            default:
                return new PlaceHolder(ItemPlaceBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof SummaryHolder) {
            ((SummaryHolder) holder).bind(row);
        } else if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row);
        } else if (holder instanceof EmptyCategoryHolder) {
            ((EmptyCategoryHolder) holder).bind(row);
        } else {
            ((PlaceHolder) holder).bind(row.place);
        }
    }

    private static void bindTile(ImageView tile, Category category) {
        Context context = tile.getContext();
        tile.setImageResource(CategoryUi.icon(category));
        tile.setImageTintList(ContextCompat.getColorStateList(context, CategoryUi.accent(category)));
        ViewCompat.setBackgroundTintList(tile, ContextCompat.getColorStateList(context, CategoryUi.container(category)));
    }

    private static final class SummaryHolder extends RecyclerView.ViewHolder {
        private final ItemPlaceSummaryBinding b;

        SummaryHolder(ItemPlaceSummaryBinding binding) {
            super(binding.getRoot());
            b = binding;
            int track = MaterialColors.getColor(itemView,
                    com.google.android.material.R.attr.colorSurfaceContainerLowest);
            // Translucent track on the primary container.
            b.zonesProgress.setTrackColor(MaterialColors.compositeARGBWithAlpha(track, 153));
            b.zonesProgress.setMax(PlaceRepository.MAX_PLACES);
        }

        void bind(Row row) {
            Context context = itemView.getContext();
            b.placeCount.setText(context.getResources().getQuantityString(R.plurals.places_count, row.count, row.count));
            b.zonesText.setText(context.getString(R.string.places_zones_active, row.zones, PlaceRepository.MAX_PLACES));
            b.zonesProgress.setProgressCompat(row.zones, false);
            b.zonesCaption.setText(context.getString(R.string.places_zones_caption, PlaceRepository.MAX_PLACES));
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemPlaceHeaderBinding b;

        HeaderHolder(ItemPlaceHeaderBinding binding) {
            super(binding.getRoot());
            b = binding;
            ViewCompat.setAccessibilityHeading(b.header, true);
        }

        void bind(Row row) {
            b.headerIcon.setImageResource(CategoryUi.icon(row.category));
            b.headerLabel.setText(CategoryUi.label(row.category));
            b.headerCount.setText(String.valueOf(row.count));
        }
    }

    private final class PlaceHolder extends RecyclerView.ViewHolder {
        private final ItemPlaceBinding b;

        PlaceHolder(ItemPlaceBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Place place) {
            Context context = itemView.getContext();
            bindTile(b.categoryTile, place.category);
            b.name.setText(place.name);
            b.coordinates.setText(context.getString(R.string.place_edit_coordinates,
                    Formatters.coordinate(place.latitude), Formatters.coordinate(place.longitude)));
            String radius = Formatters.radius(place.radiusM);
            b.radiusText.setText(radius);
            b.radiusChip.setContentDescription(context.getString(R.string.places_radius_description, radius));
            b.more.setContentDescription(context.getString(R.string.places_more_options, place.name));
            b.more.setOnClickListener(v -> listener.onPlaceMenu(place, v));
            b.card.setOnClickListener(v -> listener.onPlaceClick(place));
        }
    }

    private final class EmptyCategoryHolder extends RecyclerView.ViewHolder {
        private final ItemPlaceEmptyCategoryBinding b;

        EmptyCategoryHolder(ItemPlaceEmptyCategoryBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Row row) {
            Context context = itemView.getContext();
            String label = context.getString(CategoryUi.label(row.category));
            bindTile(b.categoryTile, row.category);
            b.emptyTitle.setText(context.getString(R.string.places_none_in_category, label));
            b.emptyBody.setText(context.getResources().getQuantityString(
                    R.plurals.places_tasks_waiting, row.count, row.count));
            b.add.setContentDescription(context.getString(R.string.places_add_in_category_description, label));
            b.add.setOnClickListener(v -> listener.onAddPlace(row.category));
        }
    }
}
