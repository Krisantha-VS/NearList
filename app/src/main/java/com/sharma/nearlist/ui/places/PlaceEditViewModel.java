package com.sharma.nearlist.ui.places;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.sharma.nearlist.NearlistApp;
import com.sharma.nearlist.R;
import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.data.repo.PlaceRepository;
import com.sharma.nearlist.location.LocationHelper;
import com.sharma.nearlist.util.AppExecutors;

/**
 * Form state for {@link AddEditPlaceActivity} (screens 14–17). Holds the coordinates, how they
 * were obtained, the location fix in progress and the typed fields, so rotation keeps them all.
 */
public class PlaceEditViewModel extends AndroidViewModel {

    public enum LoadState { LOADING, READY, NOT_FOUND }

    /** Location capture: none yet, waiting for a fix (15), location services off (16). */
    public enum CaptureState { IDLE, ACQUIRING, LOCATION_OFF }

    public enum Source { GPS, MANUAL, SAVED }

    public enum SaveResult { SAVED, LIMIT_REACHED }

    /** Immutable coordinates plus where they came from. */
    public static final class Coordinates {
        public final double latitude;
        public final double longitude;
        /** Null when typed in. */
        @Nullable
        public final Float accuracyM;
        /** Fix time for GPS, save time for SAVED. */
        public final long time;
        public final Source source;

        Coordinates(double latitude, double longitude, @Nullable Float accuracyM, long time, Source source) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracyM = accuracyM;
            this.time = time;
            this.source = source;
        }
    }

    public static final int MIN_RADIUS_M = 100;
    public static final int MAX_RADIUS_M = 1000;
    public static final int DEFAULT_RADIUS_M = 300;
    /** Spec: warn, don't block, above this accuracy. */
    public static final float LOW_ACCURACY_M = 50f;

    private final PlaceRepository placeRepository;

    private final MutableLiveData<LoadState> loadState = new MutableLiveData<>(LoadState.LOADING);
    private final MutableLiveData<CaptureState> captureState = new MutableLiveData<>(CaptureState.IDLE);
    private final MutableLiveData<Coordinates> coordinates = new MutableLiveData<>(null);
    private final MutableLiveData<String> name = new MutableLiveData<>("");
    private final MutableLiveData<Category> category = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> radius = new MutableLiveData<>(DEFAULT_RADIUS_M);
    private final MutableLiveData<Boolean> manualExpanded = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> message = new MutableLiveData<>(null);
    private final MutableLiveData<SaveResult> saveResult = new MutableLiveData<>(null);

    private boolean started;
    private long placeId;
    private Place original;
    private CancellationTokenSource fixRequest;
    private boolean saving;
    private String manualLatitudeText = "";
    private String manualLongitudeText = "";

    public PlaceEditViewModel(@NonNull Application application) {
        super(application);
        placeRepository = NearlistApp.from(application).getPlaceRepository();
    }

    /**
     * Loads the place once; later calls (after rotation) keep the form state.
     *
     * @param id     place to edit, or {@link AddEditPlaceActivity#NEW_PLACE}
     * @param preset category to pre-select for a new place, or null
     */
    public void start(long id, @Nullable Category preset) {
        if (started) {
            return;
        }
        started = true;
        placeId = id;
        if (id == AddEditPlaceActivity.NEW_PLACE) {
            category.setValue(preset);
            loadState.setValue(LoadState.READY);
            return;
        }
        AppDatabase db = AppDatabase.getInstance(getApplication());
        AppExecutors.diskIO().execute(() -> {
            Place place = db.placeDao().getByIdSync(id);
            AppExecutors.main(() -> applyLoaded(place));
        });
    }

    private void applyLoaded(Place place) {
        if (place == null) {
            loadState.setValue(LoadState.NOT_FOUND);
            return;
        }
        original = place;
        name.setValue(place.name);
        category.setValue(place.category);
        radius.setValue(clampRadius(place.radiusM));
        coordinates.setValue(new Coordinates(place.latitude, place.longitude, place.accuracyM,
                place.createdAt, Source.SAVED));
        loadState.setValue(LoadState.READY);
    }

    @Override
    protected void onCleared() {
        cancelFix();
    }

    public boolean isEditing() {
        return placeId != AddEditPlaceActivity.NEW_PLACE;
    }

    public LiveData<LoadState> getLoadState() {
        return loadState;
    }

    public LiveData<CaptureState> getCaptureState() {
        return captureState;
    }

    public LiveData<Coordinates> getCoordinates() {
        return coordinates;
    }

    public LiveData<String> getName() {
        return name;
    }

    public LiveData<Category> getCategory() {
        return category;
    }

    public LiveData<Integer> getRadius() {
        return radius;
    }

    public LiveData<Boolean> getManualExpanded() {
        return manualExpanded;
    }

    /** One-shot snackbar text; call {@link #messageShown()} after showing it. */
    public LiveData<Integer> getMessage() {
        return message;
    }

    public void messageShown() {
        message.setValue(null);
    }

    /** One-shot result of {@link #save()}. */
    public LiveData<SaveResult> getSaveResult() {
        return saveResult;
    }

    /** Requests a high-accuracy fix; the result survives rotation because it lands here. */
    public void startAcquiring() {
        if (captureState.getValue() == CaptureState.ACQUIRING) {
            return;
        }
        captureState.setValue(CaptureState.ACQUIRING);
        fixRequest = LocationHelper.getCurrentLocation(getApplication(), Priority.PRIORITY_HIGH_ACCURACY,
                this::onFix);
    }

    private void onFix(@Nullable Location location) {
        fixRequest = null;
        captureState.setValue(CaptureState.IDLE);
        if (location == null) {
            showMessage(R.string.place_edit_no_fix);
            manualExpanded.setValue(true);
            return;
        }
        Float accuracy = location.hasAccuracy() ? location.getAccuracy() : null;
        long time = location.getTime() > 0 ? location.getTime() : System.currentTimeMillis();
        coordinates.setValue(new Coordinates(location.getLatitude(), location.getLongitude(), accuracy, time,
                Source.GPS));
    }

    /** Location services are off (screen 16): stop waiting and open the manual section. */
    public void setLocationOff() {
        cancelFix();
        captureState.setValue(CaptureState.LOCATION_OFF);
        manualExpanded.setValue(true);
    }

    /** Location services came back on while the banner was showing. */
    public void clearLocationOff() {
        if (captureState.getValue() == CaptureState.LOCATION_OFF) {
            captureState.setValue(CaptureState.IDLE);
        }
    }

    public void showMessage(@StringRes int text) {
        message.setValue(text);
    }

    public void toggleManual() {
        manualExpanded.setValue(!Boolean.TRUE.equals(manualExpanded.getValue()));
    }

    public void expandManual() {
        manualExpanded.setValue(true);
    }

    /**
     * The user typed in the manual fields. Valid values replace the coordinates; anything else
     * clears them until both fields are valid again, so Save stays disabled.
     */
    public void setManualText(String latitudeText, String longitudeText) {
        manualLatitudeText = latitudeText;
        manualLongitudeText = longitudeText;
        Double lat = parseCoordinate(latitudeText, 90);
        Double lng = parseCoordinate(longitudeText, 180);
        boolean valid = lat != null && lng != null;
        coordinates.setValue(valid
                ? new Coordinates(lat, lng, null, System.currentTimeMillis(), Source.MANUAL)
                : null);
    }

    /** A fix or the saved position was copied into the manual fields; coordinates are unchanged. */
    public void setManualTextFromFix(String latitudeText, String longitudeText) {
        manualLatitudeText = latitudeText;
        manualLongitudeText = longitudeText;
    }

    public String getManualLatitudeText() {
        return manualLatitudeText;
    }

    public String getManualLongitudeText() {
        return manualLongitudeText;
    }

    public void setName(String value) {
        if (!value.equals(name.getValue())) {
            name.setValue(value);
        }
    }

    public void setCategory(@Nullable Category value) {
        if (value != category.getValue()) {
            category.setValue(value);
        }
    }

    public void setRadius(int meters) {
        int clamped = clampRadius(meters);
        Integer current = radius.getValue();
        if (current == null || current != clamped) {
            radius.setValue(clamped);
        }
    }

    public boolean canSave() {
        String current = name.getValue();
        return !saving && loadState.getValue() == LoadState.READY && coordinates.getValue() != null
                && category.getValue() != null && current != null && !current.trim().isEmpty();
    }

    /**
     * Inserts or updates the place. PlaceRepository registers its zone with INITIAL_TRIGGER_ENTER
     * (syncAll(context, placeId)), so saving while standing in it reminds straight away.
     */
    public void save() {
        if (!canSave()) {
            return;
        }
        saving = true;
        Coordinates coords = coordinates.getValue();
        Place place = original != null ? original : new Place();
        place.name = name.getValue().trim();
        place.category = category.getValue();
        place.latitude = coords.latitude;
        place.longitude = coords.longitude;
        place.accuracyM = coords.accuracyM;
        place.radiusM = radius.getValue() != null ? radius.getValue() : DEFAULT_RADIUS_M;
        if (isEditing()) {
            placeRepository.update(place);
            saveResult.setValue(SaveResult.SAVED);
            return;
        }
        placeRepository.insert(place, new PlaceRepository.InsertCallback() {
            @Override
            public void onInserted(long id) {
                saveResult.setValue(SaveResult.SAVED);
            }

            @Override
            public void onLimitReached(int maxPlaces) {
                saving = false;
                saveResult.setValue(SaveResult.LIMIT_REACHED);
            }
        });
    }

    public void saveResultHandled() {
        saveResult.setValue(null);
    }

    /** Edit mode only. PlaceRepository re-syncs, which removes the zone. */
    public void delete() {
        if (original != null) {
            placeRepository.delete(original);
        }
    }

    private void cancelFix() {
        if (fixRequest != null) {
            fixRequest.cancel();
            fixRequest = null;
        }
        if (captureState.getValue() == CaptureState.ACQUIRING) {
            captureState.setValue(CaptureState.IDLE);
        }
    }

    /** Accepts "6.5854" or "6,5854"; null when blank, not a number or outside ±limit. */
    @Nullable
    static Double parseCoordinate(String text, double limit) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim().replace(',', '.').replace('−', '-');
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(trimmed);
            return !Double.isNaN(value) && value >= -limit && value <= limit ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static int clampRadius(int meters) {
        int stepped = Math.round(meters / 50f) * 50;
        return Math.max(MIN_RADIUS_M, Math.min(MAX_RADIUS_M, stepped));
    }
}
