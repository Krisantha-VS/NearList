package com.sharma.nearlist.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.sharma.nearlist.data.entity.Place;

import java.util.List;

@Dao
public interface PlaceDao {

    /** Grouped in Category declaration order (Grocery, Fuel, Pharmacy, Other), then by name. */
    @Query("SELECT * FROM places ORDER BY "
            + "CASE category WHEN 'GROCERY' THEN 0 WHEN 'FUEL' THEN 1 WHEN 'PHARMACY' THEN 2 ELSE 3 END, "
            + "name COLLATE NOCASE")
    LiveData<List<Place>> observeAll();

    @Query("SELECT * FROM places WHERE id = :id")
    Place getByIdSync(long id);

    @Query("SELECT * FROM places")
    List<Place> getAllSync();

    @Query("SELECT COUNT(*) FROM places")
    int countSync();

    /** @param category {@code Category.key()} */
    @Query("SELECT COUNT(*) FROM places WHERE category = :category")
    LiveData<Integer> observeCountByCategory(String category);

    @Insert
    long insert(Place place);

    @Update
    void update(Place place);

    @Delete
    void delete(Place place);

    @Query("UPDATE places SET lastNotifiedAt = :lastNotifiedAt, quietUntil = :quietUntil WHERE id = :id")
    void updateNotifyState(long id, long lastNotifiedAt, long quietUntil);

    @Query("UPDATE places SET quietUntil = :quietUntil WHERE id = :id")
    void updateQuietUntil(long id, long quietUntil);

    /** Demo mode switched on: cuts every running cooldown or snooze back to maxUntil. */
    @Query("UPDATE places SET quietUntil = MIN(quietUntil, :maxUntil)")
    int clampQuietUntil(long maxUntil);

    /** Debug builds only ("Clear all data", "Load sample data"). */
    @Query("DELETE FROM places")
    void deleteAll();
}
