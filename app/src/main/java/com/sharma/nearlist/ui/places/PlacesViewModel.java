package com.sharma.nearlist.ui.places;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.data.repo.PlaceRepository;
import com.sharma.nearlist.data.repo.SettingsRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Places tab state: the zones summary, places grouped by category, and empty
 * categories that open tasks are waiting for.
 */
public class PlacesViewModel extends AndroidViewModel {

    private final PlaceRepository placeRepository;
    private final SettingsRepository settings;

    private final LiveData<List<Place>> places;
    private final LiveData<List<Task>> openTasks;
    private final MutableLiveData<Integer> zonesRegistered = new MutableLiveData<>();
    private final MediatorLiveData<List<PlaceAdapter.Row>> rows = new MediatorLiveData<>();

    /** Every sync writes the zone count; follow it so the summary updates when a sync finishes. */
    private final SharedPreferences.OnSharedPreferenceChangeListener zonesListener = (prefs, key) -> {
        if (SettingsRepository.isZonesKey(key)) {
            refreshZones();
        }
    };

    public PlacesViewModel(@NonNull Application application) {
        super(application);
        NearlistApp app = NearlistApp.from(application);
        placeRepository = app.getPlaceRepository();
        settings = app.getSettingsRepository();
        places = placeRepository.observeAll();
        openTasks = app.getTaskRepository().observeOpen();

        rows.addSource(places, v -> recompute());
        rows.addSource(openTasks, v -> recompute());
        rows.addSource(zonesRegistered, v -> recompute());
        settings.registerZonesListener(zonesListener);
        refreshZones();
    }

    @Override
    protected void onCleared() {
        settings.unregisterZonesListener(zonesListener);
    }

    /** Null until Room has delivered the first result. */
    public LiveData<List<PlaceAdapter.Row>> getRows() {
        return rows;
    }

    public LiveData<List<Place>> getPlaces() {
        return places;
    }

    /** Reads zones_registered again (refreshed in onResume). */
    public void refreshZones() {
        zonesRegistered.setValue(settings.getZonesRegistered());
    }

    public void delete(Place place) {
        placeRepository.delete(place);
    }

    private void recompute() {
        List<Place> allPlaces = places.getValue();
        List<Task> open = openTasks.getValue();
        if (allPlaces == null || open == null) {
            return;
        }
        Map<Category, List<Place>> byCategory = new EnumMap<>(Category.class);
        Map<Category, Integer> waiting = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            byCategory.put(category, new ArrayList<>());
            waiting.put(category, 0);
        }
        // observeAll is already ordered by category, then name.
        for (Place place : allPlaces) {
            byCategory.get(place.category).add(place);
        }
        for (Task task : open) {
            waiting.put(task.category, waiting.get(task.category) + 1);
        }

        List<PlaceAdapter.Row> result = new ArrayList<>();
        if (!allPlaces.isEmpty()) {
            Integer zones = zonesRegistered.getValue();
            result.add(PlaceAdapter.Row.summary(allPlaces.size(), zones != null ? zones : 0));
            for (Category category : Category.values()) {
                List<Place> inCategory = byCategory.get(category);
                int tasksWaiting = waiting.get(category);
                if (inCategory.isEmpty() && tasksWaiting == 0) {
                    continue;
                }
                result.add(PlaceAdapter.Row.header(category, inCategory.size()));
                if (inCategory.isEmpty()) {
                    result.add(PlaceAdapter.Row.emptyCategory(category, tasksWaiting));
                }
                for (Place place : inCategory) {
                    result.add(PlaceAdapter.Row.place(place));
                }
            }
        }
        rows.setValue(Collections.unmodifiableList(result));
    }
}
