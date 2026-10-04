package com.sharma.nearlist.ui.activity;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.databinding.ItemActivityDayBinding;
import com.sharma.nearlist.databinding.ItemActivityEventBinding;
import com.sharma.nearlist.domain.SuppressReason;
import com.sharma.nearlist.ui.common.CategoryUi;

import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/** Activity log: day headers and event rows on a vertical timeline. */
public class ActivityAdapter extends ListAdapter<ActivityAdapter.Row, RecyclerView.ViewHolder> {

    private static final float SUBTITLE_ICON_DP = 14f;

    /** One list row. Immutable. */
    public static final class Row {
        static final int TYPE_DAY = 0;
        static final int TYPE_EVENT = 1;

        final int type;
        /** Day rows. */
        final long dayStart;
        final int count;
        @Nullable
        final String outcomeFilter;
        /** Event rows. */
        final ActivityEvent event;
        final boolean firstOfDay;
        final boolean lastOfDay;

        private Row(int type, long dayStart, int count, @Nullable String outcomeFilter, ActivityEvent event,
                    boolean firstOfDay, boolean lastOfDay) {
            this.type = type;
            this.dayStart = dayStart;
            this.count = count;
            this.outcomeFilter = outcomeFilter;
            this.event = event;
            this.firstOfDay = firstOfDay;
            this.lastOfDay = lastOfDay;
        }

        static Row day(long dayStart, int count, @Nullable String outcomeFilter) {
            return new Row(TYPE_DAY, dayStart, count, outcomeFilter, null, false, false);
        }

        static Row event(ActivityEvent event, boolean firstOfDay, boolean lastOfDay) {
            return new Row(TYPE_EVENT, 0, 0, null, event, firstOfDay, lastOfDay);
        }

        boolean sameItem(Row other) {
            if (type != other.type) {
                return false;
            }
            return type == TYPE_DAY ? dayStart == other.dayStart : event.id == other.event.id;
        }

        boolean sameContent(Row other) {
            if (type == TYPE_DAY) {
                return count == other.count && Objects.equals(outcomeFilter, other.outcomeFilter);
            }
            // Log rows are never edited, so the id and the timeline position say it all.
            return firstOfDay == other.firstOfDay && lastOfDay == other.lastOfDay;
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

    public ActivityAdapter() {
        super(DIFF);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == Row.TYPE_DAY) {
            return new DayHolder(ItemActivityDayBinding.inflate(inflater, parent, false));
        }
        return new EventHolder(ItemActivityEventBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = getItem(position);
        if (holder instanceof DayHolder) {
            ((DayHolder) holder).bind(row);
        } else {
            ((EventHolder) holder).bind(row);
        }
    }

    /** "Today", "Yesterday", otherwise "Wed, 8 Oct" (with the year when it isn't this year). */
    static String dayLabel(Context context, long dayStart) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        if (dayStart == today.getTimeInMillis()) {
            return context.getString(R.string.activity_today);
        }
        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        if (dayStart == yesterday.getTimeInMillis()) {
            return context.getString(R.string.activity_yesterday);
        }
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(dayStart);
        int flags = DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL;
        if (day.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            flags |= DateUtils.FORMAT_NO_YEAR;
        }
        return DateUtils.formatDateTime(context, dayStart, flags);
    }

    /**
     * Rows from the debug "Simulate arrival" menu have "(simulated)" in their detail. Returns the
     * detail without that marker, or null if the event was not simulated.
     */
    @Nullable
    static String stripSimulated(Context context, @Nullable String detail) {
        if (detail == null) {
            return null;
        }
        String marker = context.getString(R.string.log_detail_simulated);
        if (detail.equals(marker)) {
            return "";
        }
        String suffix = " " + marker;
        return detail.endsWith(suffix) ? detail.substring(0, detail.length() - suffix.length()) : null;
    }

    private static final class DayHolder extends RecyclerView.ViewHolder {
        private final ItemActivityDayBinding b;

        DayHolder(ItemActivityDayBinding binding) {
            super(binding.getRoot());
            b = binding;
            ViewCompat.setAccessibilityHeading(b.header, true);
        }

        void bind(Row row) {
            Context context = itemView.getContext();
            b.day.setText(dayLabel(context, row.dayStart));
            int plural;
            if (ActivityEvent.OUTCOME_NOTIFIED.equals(row.outcomeFilter)) {
                plural = R.plurals.activity_count_notified;
            } else if (ActivityEvent.OUTCOME_SUPPRESSED.equals(row.outcomeFilter)) {
                plural = R.plurals.activity_count_suppressed;
            } else {
                plural = R.plurals.activity_count_events;
            }
            b.count.setText(context.getResources().getQuantityString(plural, row.count, row.count));
        }
    }

    private static final class EventHolder extends RecyclerView.ViewHolder {
        private final ItemActivityEventBinding b;

        EventHolder(ItemActivityEventBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void bind(Row row) {
            Context context = itemView.getContext();
            ActivityEvent event = row.event;
            b.lineTop.setVisibility(row.firstOfDay ? View.INVISIBLE : View.VISIBLE);
            b.lineBottom.setVisibility(row.lastOfDay ? View.INVISIBLE : View.VISIBLE);
            String time = DateFormat.getTimeFormat(context).format(new Date(event.timestamp));
            b.time.setText(time);

            String simulatedDetail = stripSimulated(context, event.detail);
            boolean simulated = simulatedDetail != null;
            String detail = simulated ? (simulatedDetail.isEmpty() ? null : simulatedDetail) : event.detail;
            b.simulated.setVisibility(simulated ? View.VISIBLE : View.GONE);

            boolean system = ActivityEvent.TYPE_SYSTEM.equals(event.type);
            if (system) {
                bindSystem(context, event);
            } else {
                bindPlace(context, event);
            }
            bindOutcome(context, event, detail);

            String description = time + ", " + b.title.getText() + ", " + b.subtitle.getText() + ", "
                    + b.outcomeText.getText() + (simulated ? ", " + context.getString(R.string.activity_simulated) : "");
            b.row.setContentDescription(description);
        }

        private void bindPlace(Context context, ActivityEvent event) {
            Category category = event.category != null ? event.category : Category.OTHER;
            b.tile.setBackgroundResource(R.drawable.shape_rounded_8);
            b.tile.setImageResource(CategoryUi.icon(category));
            b.tile.setImageTintList(ContextCompat.getColorStateList(context, CategoryUi.accent(category)));
            ViewCompat.setBackgroundTintList(b.tile, ContextCompat.getColorStateList(context, CategoryUi.container(category)));
            b.title.setText(event.placeName);
            String label = context.getString(CategoryUi.label(category));
            boolean exit = ActivityEvent.TYPE_EXIT.equals(event.type);
            b.subtitle.setText(context.getString(exit ? R.string.activity_exited : R.string.activity_entered, label));
            setSubtitleIcon(context, exit ? R.drawable.ic_event_exit : R.drawable.ic_event_enter);
        }

        private void bindSystem(Context context, ActivityEvent event) {
            String kind = event.reason;
            boolean restart = ActivityEvent.SYSTEM_RESTART.equals(kind) || ActivityEvent.SYSTEM_UPDATE.equals(kind);
            b.tile.setBackgroundResource(R.drawable.shape_circle);
            b.tile.setImageResource(restart ? R.drawable.ic_event_system : R.drawable.ic_settings);
            b.tile.setImageTintList(ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOnTertiaryContainer)));
            ViewCompat.setBackgroundTintList(b.tile,
                    ColorStateList.valueOf(color(com.google.android.material.R.attr.colorTertiaryContainer)));
            int title;
            if (ActivityEvent.SYSTEM_RESTART.equals(kind)) {
                title = R.string.activity_system_restart;
            } else if (ActivityEvent.SYSTEM_UPDATE.equals(kind)) {
                title = R.string.activity_system_update;
            } else if (ActivityEvent.SYSTEM_ZONES.equals(kind)) {
                title = R.string.activity_system_zones;
            } else if (ActivityEvent.SYSTEM_LOCATION_ERROR.equals(kind)) {
                title = R.string.activity_system_location_error;
            } else {
                title = R.string.activity_system_other;
            }
            b.title.setText(title);
            b.subtitle.setText(R.string.activity_system);
            setSubtitleIcon(context, R.drawable.ic_settings);
        }

        private void bindOutcome(Context context, ActivityEvent event, @Nullable String detail) {
            @DrawableRes int icon;
            String text;
            @AttrRes int background = com.google.android.material.R.attr.colorSurfaceContainerHigh;
            @AttrRes int content = com.google.android.material.R.attr.colorOnSurfaceVariant;
            boolean outlined = false;
            switch (event.outcome) {
                case ActivityEvent.OUTCOME_NOTIFIED:
                    icon = R.drawable.ic_check_circle_filled;
                    text = context.getResources().getQuantityString(R.plurals.activity_outcome_notified,
                            event.taskCount, event.taskCount);
                    background = com.google.android.material.R.attr.colorPrimaryContainer;
                    content = com.google.android.material.R.attr.colorOnPrimaryContainer;
                    break;
                case ActivityEvent.OUTCOME_SUPPRESSED:
                    icon = suppressedIcon(event.reason);
                    text = suppressedText(context, event.reason, detail);
                    break;
                case ActivityEvent.OUTCOME_LOGGED:
                    icon = R.drawable.ic_outcome_logged;
                    text = context.getString(R.string.activity_outcome_logged);
                    outlined = true;
                    break;
                default:
                    icon = R.drawable.ic_sync;
                    text = detail != null ? detail : "";
                    background = com.google.android.material.R.attr.colorTertiaryContainer;
                    content = com.google.android.material.R.attr.colorOnTertiaryContainer;
                    break;
            }
            if (outlined) {
                b.outcome.setBackgroundResource(R.drawable.shape_chip_outline_variant);
                ViewCompat.setBackgroundTintList(b.outcome, null);
            } else {
                b.outcome.setBackgroundResource(R.drawable.shape_rounded_8);
                ViewCompat.setBackgroundTintList(b.outcome, ColorStateList.valueOf(color(background)));
            }
            int contentColor = color(content);
            b.outcomeIcon.setImageResource(icon);
            b.outcomeIcon.setImageTintList(ColorStateList.valueOf(contentColor));
            b.outcomeText.setText(text);
            b.outcomeText.setTextColor(contentColor);
        }

        @DrawableRes
        private static int suppressedIcon(@Nullable String reason) {
            if (SuppressReason.COOLDOWN.name().equals(reason)) {
                return R.drawable.ic_notifications_paused;
            } else if (SuppressReason.OUTSIDE_TIME_WINDOW.name().equals(reason)) {
                return R.drawable.ic_schedule;
            } else if (SuppressReason.NO_OPEN_TASKS.name().equals(reason)) {
                return R.drawable.ic_outcome_no_tasks;
            }
            // No reason: notifications were blocked when the reminder was due.
            return R.drawable.ic_banner_notifications_off;
        }

        private static String suppressedText(Context context, @Nullable String reason, @Nullable String detail) {
            if (SuppressReason.COOLDOWN.name().equals(reason)) {
                return detail != null ? context.getString(R.string.activity_outcome_cooldown_detail, detail)
                        : context.getString(R.string.activity_outcome_cooldown);
            } else if (SuppressReason.OUTSIDE_TIME_WINDOW.name().equals(reason)) {
                return detail != null ? context.getString(R.string.activity_outcome_outside_window_detail, detail)
                        : context.getString(R.string.activity_outcome_outside_window);
            } else if (SuppressReason.NO_OPEN_TASKS.name().equals(reason)) {
                return context.getString(R.string.activity_outcome_no_open_tasks);
            } else if (reason == null) {
                return context.getString(R.string.activity_outcome_blocked);
            }
            return detail != null ? context.getString(R.string.activity_outcome_suppressed_detail, detail)
                    : context.getString(R.string.activity_outcome_suppressed);
        }

        private void setSubtitleIcon(Context context, @DrawableRes int icon) {
            Drawable drawable = ContextCompat.getDrawable(context, icon);
            if (drawable == null) {
                return;
            }
            drawable = drawable.mutate();
            int size = Math.round(SUBTITLE_ICON_DP * context.getResources().getDisplayMetrics().density);
            drawable.setBounds(0, 0, size, size);
            drawable.setTint(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
            TextViewCompat.setCompoundDrawablesRelative(b.subtitle, drawable, null, null, null);
        }

        private int color(@AttrRes int attr) {
            return MaterialColors.getColor(itemView, attr);
        }
    }
}
