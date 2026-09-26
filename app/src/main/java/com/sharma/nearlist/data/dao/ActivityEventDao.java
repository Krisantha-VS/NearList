package com.sharma.nearlist.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.sharma.nearlist.data.entity.ActivityEvent;

import java.util.List;

@Dao
public interface ActivityEventDao {

    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC, id DESC")
    LiveData<List<ActivityEvent>> observeAll();

    /** @param outcome one of the {@code ActivityEvent.OUTCOME_} constants */
    @Query("SELECT * FROM activity_events WHERE outcome = :outcome ORDER BY timestamp DESC, id DESC")
    LiveData<List<ActivityEvent>> observeByOutcome(String outcome);

    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC, id DESC")
    List<ActivityEvent> getAllSync();

    @Query("SELECT COUNT(*) FROM activity_events")
    LiveData<Integer> observeCount();

    @Insert
    long insert(ActivityEvent event);

    @Query("DELETE FROM activity_events")
    void clear();
}
