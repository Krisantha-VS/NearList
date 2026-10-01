package com.sharma.nearlist.notify;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.sharma.nearlist.R;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.receiver.NotificationActionReceiver;
import com.sharma.nearlist.ui.common.CategoryUi;
import com.sharma.nearlist.ui.main.MainActivity;
import com.sharma.nearlist.util.PermissionHelper;

import java.util.ArrayList;
import java.util.List;

/** Grouped place reminders. */
public final class NotificationHelper {

    private static final String TAG = "Nearlist";

    public static final String CHANNEL_REMINDERS = "reminders";

    /** Large-icon tile corner radius relative to its size (12 dp on a 40 dp tile in the designs). */
    private static final float TILE_CORNER_RATIO = 12f / 40f;
    /** Icon size relative to the tile (24 dp on 40 dp). */
    private static final float TILE_ICON_RATIO = 24f / 40f;

    private NotificationHelper() {
    }

    /** Creates the reminders channel. Safe to call repeatedly; does nothing below API 26. */
    public static void createChannel(Context context) {
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(
                CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_reminders_name))
                .setDescription(context.getString(R.string.notification_channel_reminders_description))
                .build();
        NotificationManagerCompat.from(context).createNotificationChannel(channel);
    }

    /**
     * True when a reminder would actually be shown: POST_NOTIFICATIONS granted on API 33+, app
     * notifications enabled, and the reminders channel not switched off.
     */
    public static boolean canPostNotifications(Context context) {
        if (!PermissionHelper.hasNotificationPermission(context)) {
            return false;
        }
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_REMINDERS);
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    /**
     * One notification per place (id = place id), replacing any earlier one for that place.
     *
     * @return false if notifications can't be posted, so the caller can log it as suppressed
     */
    @SuppressLint("MissingPermission")
    public static boolean showPlaceReminder(Context context, Place place, List<Task> tasks) {
        if (tasks.isEmpty() || !canPostNotifications(context)) {
            return false;
        }
        Category category = place.category;
        List<String> titles = new ArrayList<>(tasks.size());
        long[] taskIds = new long[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            titles.add(tasks.get(i).title);
            taskIds[i] = tasks.get(i).id;
        }
        String title = context.getString(R.string.notification_title_near_place, place.name);
        String collapsed = TextUtils.join(context.getString(R.string.notification_task_separator), titles);
        String expanded = TextUtils.join("\n", titles);
        String taskCount = context.getResources().getQuantityString(
                R.plurals.notification_task_count, tasks.size(), tasks.size());
        int doneLabel = tasks.size() == 1
                ? R.string.notification_action_mark_done
                : R.string.notification_action_mark_all_done;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_nearlist)
                .setLargeIcon(categoryTile(context, category))
                .setColor(lightAccent(context, category))
                .setContentTitle(title)
                .setContentText(collapsed)
                .setSubText(context.getString(CategoryUi.label(category)))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(expanded)
                        .setSummaryText(taskCount))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(contentIntent(context, place.id))
                .addAction(R.drawable.ic_check, context.getString(doneLabel),
                        NotificationActionReceiver.markDoneIntent(context, place.id, taskIds))
                .addAction(R.drawable.ic_notifications_paused,
                        context.getString(R.string.notification_action_snooze),
                        NotificationActionReceiver.snoozeIntent(context, place.id));
        try {
            NotificationManagerCompat.from(context).notify(notificationId(place.id), builder.build());
            return true;
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission revoked", e);
            return false;
        }
    }

    public static void cancel(Context context, long placeId) {
        NotificationManagerCompat.from(context).cancel(notificationId(placeId));
    }

    public static int notificationId(long placeId) {
        return (int) placeId;
    }

    private static PendingIntent contentIntent(Context context, long placeId) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, notificationId(placeId), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Light-theme accent, as the notification design specifies; the system adapts it for dark shades. */
    private static int lightAccent(Context context, Category category) {
        Configuration light = new Configuration(context.getResources().getConfiguration());
        light.uiMode = (light.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        return context.createConfigurationContext(light).getColor(CategoryUi.accent(category));
    }

    /** Category icon on its container colour, like the list tiles; follows the current day/night mode. */
    private static Bitmap categoryTile(Context context, Category category) {
        int size = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(context, CategoryUi.container(category)));
        float radius = size * TILE_CORNER_RATIO;
        canvas.drawRoundRect(new RectF(0, 0, size, size), radius, radius, paint);

        Drawable icon = ContextCompat.getDrawable(context, CategoryUi.icon(category));
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(ContextCompat.getColor(context, CategoryUi.accent(category)));
            int iconSize = Math.round(size * TILE_ICON_RATIO);
            int inset = (size - iconSize) / 2;
            icon.setBounds(inset, inset, inset + iconSize, inset + iconSize);
            icon.draw(canvas);
        }
        return bitmap;
    }
}
