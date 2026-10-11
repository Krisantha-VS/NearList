package com.sharma.nearlist.ui.settings;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.repo.SettingsRepository;
import com.sharma.nearlist.location.GeofenceManager;
import com.sharma.nearlist.util.AppExecutors;

/** Settings state. Each change that affects zone registration re-syncs geofences. */
public class SettingsViewModel extends AndroidViewModel {

    /** Registered zones and when the last successful sync finished (0 = never / failed). */
    public static final class Zones {
        public final int count;
        public final long registeredAt;

        Zones(int count, long registeredAt) {
            this.count = count;
            this.registeredAt = registeredAt;
        }
    }

    /** Outcome of "Re-register all reminder zones". */
    public static final class ReregisterResult {
        public final int zones;
        public final int places;

        ReregisterResult(int zones, int places) {
            this.zones = zones;
            this.places = places;
        }
    }

    private final SettingsRepository settings;
    private final MutableLiveData<Zones> zones = new MutableLiveData<>();
    private final MutableLiveData<ReregisterResult> reregisterResult = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> reregistering = new MutableLiveData<>(false);

    private final SharedPreferences.OnSharedPreferenceChangeListener zonesListener = (prefs, key) -> {
        if (SettingsRepository.isZonesKey(key)) {
            refreshZones();
        }
    };

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        settings = NearlistApp.from(application).getSettingsRepository();
        settings.registerZonesListener(zonesListener);
        refreshZones();
    }

    @Override
    protected void onCleared() {
        settings.unregisterZonesListener(zonesListener);
    }

    public LiveData<Zones> getZones() {
        return zones;
    }

    public LiveData<Boolean> isReregistering() {
        return reregistering;
    }

    /** One-shot; call {@link #reregisterResultShown()} after showing it. */
    public LiveData<ReregisterResult> getReregisterResult() {
        return reregisterResult;
    }

    public void reregisterResultShown() {
        reregisterResult.setValue(null);
    }

    public void refreshZones() {
        zones.setValue(new Zones(settings.getZonesRegistered(), settings.getZonesRegisteredAt()));
    }

    public int getCooldownHours() {
        return settings.getCooldownHours();
    }

    /** Takes effect on the next reminder; registered zones don't depend on it. */
    public void setCooldownHours(int hours) {
        settings.setCooldownHours(hours);
    }

    public boolean isBatteryAware() {
        return settings.isBatteryAware();
    }

    /** Changes the responsiveness band, so the zones are registered again. */
    public void setBatteryAware(boolean batteryAware) {
        if (batteryAware != settings.isBatteryAware()) {
            settings.setBatteryAware(batteryAware);
            GeofenceManager.syncAll(getApplication());
        }
    }

    /** False once demo mode has expired, even if it was switched on less than a restart ago. */
    public boolean isDemoActive() {
        return settings.isDemoActive();
    }

    /**
     * Demo mode sets responsiveness to 0 ms and the cooldown to 10 s; zones are registered again.
     * Switching it on also cuts any running cooldown to 10 s. Switching it off leaves them alone.
     */
    public void setDemoEnabled(boolean enabled) {
        if (enabled != settings.isDemoActive()) {
            settings.setDemoEnabled(enabled);
            if (enabled) {
                long maxUntil = settings.getDemoQuietCap();
                Application app = getApplication();
                AppExecutors.diskIO().execute(() ->
                        AppDatabase.getInstance(app).placeDao().clampQuietUntil(maxUntil));
            }
            GeofenceManager.syncAll(getApplication());
        }
    }

    /** Re-registers every zone on the disk thread and reports how many succeeded. */
    public void reregisterAll() {
        if (Boolean.TRUE.equals(reregistering.getValue())) {
            return;
        }
        reregistering.setValue(true);
        Application app = getApplication();
        AppExecutors.diskIO().execute(() -> {
            int registered = GeofenceManager.syncAllBlocking(app, GeofenceManager.NO_CHANGED_PLACE);
            int places = AppDatabase.getInstance(app).placeDao().countSync();
            AppExecutors.main(() -> {
                reregistering.setValue(false);
                refreshZones();
                reregisterResult.setValue(new ReregisterResult(registered, places));
            });
        });
    }
}
