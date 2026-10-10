package com.sharma.nearlist.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.sharma.nearlist.data.entity.Task;

import java.util.List;

@Dao
public interface TaskDao {

    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY createdAt")
    LiveData<List<Task>> observeOpen();

    @Query("SELECT * FROM tasks WHERE done = 1 ORDER BY completedAt DESC")
    LiveData<List<Task>> observeCompleted();

    @Query("SELECT * FROM tasks WHERE id = :id")
    Task getByIdSync(long id);

    /** @param category {@code Category.key()} */
    @Query("SELECT * FROM tasks WHERE done = 0 AND category = :category ORDER BY createdAt")
    List<Task> getOpenByCategorySync(String category);

    @Insert
    long insert(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("UPDATE tasks SET done = 1, completedAt = :completedAt WHERE id IN (:ids)")
    void markDone(long[] ids, long completedAt);

    @Query("UPDATE tasks SET done = 0, completedAt = 0 WHERE id = :id")
    void markOpen(long id);

    /** Debug builds only ("Clear all data", "Load sample data"). */
    @Query("DELETE FROM tasks")
    void deleteAll();
}
