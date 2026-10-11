package com.sharma.nearlist.ui.tasks;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.databinding.ItemSectionHeaderBinding;
import com.sharma.nearlist.databinding.ItemTaskBinding;
import com.sharma.nearlist.databinding.ItemTaskCompletedBinding;
import com.sharma.nearlist.domain.TaskListItem;
import com.sharma.nearlist.ui.common.CategoryUi;
import com.sharma.nearlist.util.Formatters;

import java.util.Objects;

/** Home list: open tasks (with or without a place), the "Completed" header and completed tasks. */
public class TaskAdapter extends ListAdapter<TaskAdapter.Row, RecyclerView.ViewHolder> {

    public interface Listener {
        void onTaskClick(Task task);

        void onDoneChanged(Task task, boolean done);

        void onCompletedHeaderClick();
    }

    /** One list row. Immutable. */
    public static final class Row {
        static final int TYPE_TASK = 0;
        static final int TYPE_HEADER = 1;
        static final int TYPE_COMPLETED = 2;

        final int type;
        final TaskListItem item;
        final Task task;
        final boolean nearestOverall;
        final int count;
        final boolean expanded;

        private Row(int type, TaskListItem item, Task task, boolean nearestOverall, int count, boolean expanded) {
            this.type = type;
            this.item = item;
            this.task = task;
            this.nearestOverall = nearestOverall;
            this.count = count;
            this.expanded = expanded;
        }

        /** @param nearestOverall true for the closest task overall; its distance chip is highlighted */
        static Row open(TaskListItem item, boolean nearestOverall) {
            return new Row(TYPE_TASK, item, item.task, nearestOverall, 0, false);
        }

        static Row header(int count, boolean expanded) {
            return new Row(TYPE_HEADER, null, null, false, count, expanded);
        }

        static Row completed(Task task) {
            return new Row(TYPE_COMPLETED, null, task, false, 0, false);
        }

        boolean sameItem(Row other) {
            return type == other.type && (type == TYPE_HEADER || task.id == other.task.id);
        }

        boolean sameContent(Row other) {
            if (type == TYPE_HEADER) {
                return count == other.count && expanded == other.expanded;
            }
            Task a = task;
            Task b = other.task;
            boolean sameTask = a.title.equals(b.title) && a.category == b.category
                    && a.hasTimeWindow == b.hasTimeWindow && a.windowStartMin == b.windowStartMin
                    && a.windowEndMin == b.windowEndMin && a.done == b.done && a.completedAt == b.completedAt;
            if (type == TYPE_COMPLETED) {
                return sameTask;
            }
            return sameTask && nearestOverall == other.nearestOverall
                    && samePlace(item.nearestPlace, other.item.nearestPlace)
                    && Objects.equals(distanceText(item), distanceText(other.item));
        }

        private static boolean samePlace(Place a, Place b) {
            if (a == null || b == null) {
                return a == b;
            }
            return a.id == b.id && a.name.equals(b.name);
        }

        private static String distanceText(TaskListItem item) {
            return item.distanceMeters == null ? null : Formatters.distance(item.distanceMeters);
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

    public TaskAdapter(Listener listener) {
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
            case Row.TYPE_HEADER:
                return new HeaderHolder(ItemSectionHeaderBinding.inflate(inflater, parent, false));
            case Row.TYPE_COMPLETED:
                return new CompletedHolder(ItemTaskCompletedBinding.inflate(inflater, parent, false));
            default:
                return new TaskHolder(ItemTaskBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof TaskHolder) {
            ((TaskHolder) holder).bind(row);
        } else if (holder instanceof CompletedHolder) {
            ((CompletedHolder) holder).bind(row.task);
        } else {
            ((HeaderHolder) holder).bind(row);
        }
    }

    private final class TaskHolder extends RecyclerView.ViewHolder {
        private final ItemTaskBinding b;

        TaskHolder(ItemTaskBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Row row) {
            Context context = itemView.getContext();
            Task task = row.task;
            TaskListItem item = row.item;

            b.categoryTile.setImageResource(CategoryUi.icon(task.category));
            b.categoryTile.setImageTintList(ContextCompat.getColorStateList(context, CategoryUi.accent(task.category)));
            ViewCompat.setBackgroundTintList(b.categoryTile,
                    ContextCompat.getColorStateList(context, CategoryUi.container(task.category)));
            b.title.setText(task.title);

            boolean hasPlace = item.nearestPlace != null;
            boolean hasDistance = item.distanceMeters != null;
            // "Nearest:" only when a distance proves it is the nearest (location known).
            b.nearestPlace.setVisibility(hasDistance ? View.VISIBLE : View.GONE);
            if (hasDistance) {
                b.nearestPlace.setText(context.getString(R.string.tasks_nearest_place, item.nearestPlace.name));
            }
            b.noPlaceRow.setVisibility(hasPlace ? View.GONE : View.VISIBLE);

            b.distanceChip.setVisibility(hasDistance ? View.VISIBLE : View.GONE);
            if (hasDistance) {
                String distance = Formatters.distance(item.distanceMeters);
                b.distanceText.setText(distance);
                b.distanceChip.setContentDescription(context.getString(R.string.tasks_distance_description, distance));
                int background = MaterialColors.getColor(itemView, row.nearestOverall
                        ? com.google.android.material.R.attr.colorPrimaryContainer
                        : com.google.android.material.R.attr.colorSecondaryContainer);
                int content = MaterialColors.getColor(itemView, row.nearestOverall
                        ? com.google.android.material.R.attr.colorOnPrimaryContainer
                        : com.google.android.material.R.attr.colorOnSecondaryContainer);
                ViewCompat.setBackgroundTintList(b.distanceChip, ColorStateList.valueOf(background));
                b.distanceText.setTextColor(content);
                b.distanceIcon.setImageTintList(ColorStateList.valueOf(content));
            }

            String window = Formatters.timeWindow(task);
            b.windowChip.setVisibility(window != null ? View.VISIBLE : View.GONE);
            if (window != null) {
                b.windowText.setText(window);
                b.windowChip.setContentDescription(context.getString(R.string.tasks_window_description, window));
            }
            b.chipRow.setVisibility(hasDistance || window != null ? View.VISIBLE : View.GONE);

            b.doneCheck.setOnCheckedChangeListener(null);
            b.doneCheck.setChecked(false);
            b.doneCheck.setContentDescription(context.getString(R.string.tasks_mark_done, task.title));
            b.doneCheck.setOnCheckedChangeListener((button, checked) -> listener.onDoneChanged(task, checked));
            b.card.setOnClickListener(v -> listener.onTaskClick(task));
        }
    }

    private final class CompletedHolder extends RecyclerView.ViewHolder {
        private final ItemTaskCompletedBinding b;

        CompletedHolder(ItemTaskCompletedBinding binding) {
            super(binding.getRoot());
            b = binding;
            b.title.setPaintFlags(b.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        void bind(Task task) {
            Context context = itemView.getContext();
            b.categoryTile.setImageResource(CategoryUi.icon(task.category));
            b.title.setText(task.title);
            b.doneAt.setText(Formatters.doneAt(context, task.completedAt));
            b.doneCheck.setOnCheckedChangeListener(null);
            b.doneCheck.setChecked(true);
            b.doneCheck.setContentDescription(context.getString(R.string.tasks_mark_not_done, task.title));
            b.doneCheck.setOnCheckedChangeListener((button, checked) -> listener.onDoneChanged(task, checked));
            b.card.setOnClickListener(v -> listener.onTaskClick(task));
        }
    }

    private final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemSectionHeaderBinding b;

        HeaderHolder(ItemSectionHeaderBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Row row) {
            b.count.setText(String.valueOf(row.count));
            b.expandIcon.setImageResource(row.expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
            b.header.setContentDescription(itemView.getContext().getString(
                    row.expanded ? R.string.tasks_completed_collapse : R.string.tasks_completed_expand));
            b.header.setOnClickListener(v -> listener.onCompletedHeaderClick());
        }
    }
}
