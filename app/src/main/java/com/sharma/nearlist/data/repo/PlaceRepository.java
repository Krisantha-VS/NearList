package com.sharma.nearlist.data.repo;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.sharma.nearlist.data.dao.PlaceDao;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.model.Category;
import com.sharma.nearlist.location.GeofenceManager;
import com.sharma.nearlist.util.AppExecutors;

import java.util.List;

public class PlaceRepository {

    /** Play services allows at most 100 geofences per app (spec section 7). */
    public static final int MAX_PLACES = 100;

    /**
     * One-shot result of {@link #insert}, delivered on the main thread. A callback rather than
     * a LiveData error: LiveData re-delivers its last value to new observers, so the
     * "limit reached" message would show again after rotation.
     */
    public interface InsertCallback {
        void onInserted(long id);

        void onLimitReached(int maxPlaces);
    }

    private final Context appContext;
    private final PlaceDao dao;

    public PlaceRepository(Context context, PlaceDao dao) {
        this.appContext = context.getApplicationContext();
        this.dao = dao;
    }

    public LiveData<List<Place>> observeAll() {
        return dao.observeAll();
    }

    public LiveData<Integer> observeCountByCategory(Category category) {
        return dao.observeCountByCategory(category.key());
    }

    /**
     * Inserts the place unless {@link #MAX_PLACES} already exist. The count check and the
     * insert run in one task on the single disk thread, so two concurrent saves can't both
     * pass the check.
     */
    public void insert(Place place, @Nullable InsertCallback callback) {
        AppExecutors.diskIO().execute(() -> {
            if (dao.countSync() >= MAX_PLACES) {
                if (callback != null) {
                    AppExecutors.main(() -> callback.onLimitReached(MAX_PLACES));
                }
                return;
            }
            if (place.createdAt == 0) {
                place.createdAt = System.currentTimeMillis();
            }
            long id = dao.insert(place);
            GeofenceManager.syncAll(appContext, id);
            if (callback != null) {
                AppExecutors.main(() -> callback.onInserted(id));
            }
        });
    }

    public void update(Place place) {
        AppExecutors.diskIO().execute(() -> {
            dao.update(place);
            GeofenceManager.syncAll(appContext, place.id);
        });
    }

    public void delete(Place place) {
        AppExecutors.diskIO().execute(() -> {
            dao.delete(place);
            GeofenceManager.syncAll(appContext);
        });
    }
}
