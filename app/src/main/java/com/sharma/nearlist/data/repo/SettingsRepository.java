package com.sharma.nearlist.data.repo;

import android.content.Context;
import android.content.SharedPreferences;

/** Small key/value settings in SharedPreferences. */
public class SettingsRepository {

    /** Time source, injectable so the demo-mode expiry can be tested. */
    public interface Clock {
        long now();
    }

    public static final String PREFS_NAME = "nearlist_settings";

    static final String KEY_ONBOARDING_DONE = "onboarding_done";
    static final String KEY_COOLDOWN_HOURS = "cooldown_hours";
    static final String KEY_BATTERY_AWARE = "battery_aware";
    static final String KEY_DEMO_ENABLED_AT = "demo_enabled_at";
    static final String KEY_ZONES_REGISTERED = "zones_registered";
    static final String KEY_ZONES_REGISTERED_AT = "zones_registered_at";

    public static final int[] COOLDOWN_HOUR_OPTIONS = {1, 4, 8, 24};
    public static final int DEFAULT_COOLDOWN_HOURS = 4;
    public static final long DEMO_DURATION_MS = 3_600_000L;
    public static final long DEMO_COOLDOWN_MS = 10_000L;
    private static final long HOUR_MS = 3_600_000L;

    private final SharedPreferences prefs;
    private final Clock clock;

    /** In memory only: the Home banner stays dismissed until the process restarts. */
    private volatile boolean bannerDismissedSession;

    public SettingsRepository(SharedPreferences prefs, Clock clock) {
        this.prefs = prefs;
        this.clock = clock;
    }

    public static SettingsRepository create(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new SettingsRepository(prefs, System::currentTimeMillis);
    }

    // Onboarding

    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    // Cooldown

    public int getCooldownHours() {
        return prefs.getInt(KEY_COOLDOWN_HOURS, DEFAULT_COOLDOWN_HOURS);
    }

    /** @throws IllegalArgumentException if hours is not one of {@link #COOLDOWN_HOUR_OPTIONS} */
    public void setCooldownHours(int hours) {
        for (int option : COOLDOWN_HOUR_OPTIONS) {
            if (option == hours) {
                prefs.edit().putInt(KEY_COOLDOWN_HOURS, hours).apply();
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported cooldown: " + hours + " h");
    }

    public long getCooldownMs() {
        return cooldownMs(isDemoActive(), getCooldownHours());
    }

    // Battery

    public boolean isBatteryAware() {
        return prefs.getBoolean(KEY_BATTERY_AWARE, true);
    }

    public void setBatteryAware(boolean batteryAware) {
        prefs.edit().putBoolean(KEY_BATTERY_AWARE, batteryAware).apply();
    }

    // Demo mode

    public long getDemoEnabledAt() {
        return prefs.getLong(KEY_DEMO_ENABLED_AT, 0L);
    }

    /** Stores the current time when enabled, 0 when disabled. */
    public void setDemoEnabled(boolean enabled) {
        prefs.edit().putLong(KEY_DEMO_ENABLED_AT, enabled ? clock.now() : 0L).apply();
    }

    public boolean isDemoActive() {
        return isDemoActive(getDemoEnabledAt(), clock.now());
    }

    /** Latest quietUntil allowed once demo mode is switched on, so a running cooldown ends within 10 s. */
    public long getDemoQuietCap() {
        return demoQuietCap(clock.now());
    }

    // Geofence registration status

    public int getZonesRegistered() {
        return prefs.getInt(KEY_ZONES_REGISTERED, 0);
    }

    public long getZonesRegisteredAt() {
        return prefs.getLong(KEY_ZONES_REGISTERED_AT, 0L);
    }

    /**
     * Listens for changes to the registered-zone count (written by every sync), on the main thread.
     * Keep a strong reference to the listener and unregister it again.
     */
    public void registerZonesListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterZonesListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static boolean isZonesKey(String key) {
        return KEY_ZONES_REGISTERED.equals(key);
    }

    public void setZonesRegistered(int count, long registeredAt) {
        prefs.edit()
                .putInt(KEY_ZONES_REGISTERED, count)
                .putLong(KEY_ZONES_REGISTERED_AT, registeredAt)
                .apply();
    }

    // Home banner (session only)

    public boolean isBannerDismissedSession() {
        return bannerDismissedSession;
    }

    public void setBannerDismissedSession(boolean dismissed) {
        bannerDismissedSession = dismissed;
    }

    // Derived-value rules, pure so they can be unit-tested on the JVM.

    static boolean isDemoActive(long demoEnabledAt, long now) {
        return demoEnabledAt > 0 && now - demoEnabledAt < DEMO_DURATION_MS;
    }

    static long cooldownMs(boolean demoActive, int cooldownHours) {
        return demoActive ? DEMO_COOLDOWN_MS : cooldownHours * HOUR_MS;
    }

    static long demoQuietCap(long now) {
        return now + DEMO_COOLDOWN_MS;
    }

    /** Same rule as {@code PlaceDao.clampQuietUntil}: shortens a later quietUntil, never extends one. */
    static long clampQuietUntil(long quietUntil, long maxUntil) {
        return Math.min(quietUntil, maxUntil);
    }
}
