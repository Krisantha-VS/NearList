package com.sharma.nearlist.data.repo;

import androidx.lifecycle.LiveData;

import com.sharma.nearlist.data.dao.TaskDao;
import com.sharma.nearlist.data.entity.Task;
import com.sharma.nearlist.util.AppExecutors;

import java.util.List;

/** Task writes don't sync geofences: zones depend only on places. */
public class TaskRepository {

    private final TaskDao dao;

    public TaskRepository(TaskDao dao) {
        this.dao = dao;
    }

    public LiveData<List<Task>> observeOpen() {
        return dao.observeOpen();
    }

    public LiveData<List<Task>> observeCompleted() {
        return dao.observeCompleted();
    }

    public void insert(Task task) {
        AppExecutors.diskIO().execute(() -> {
            if (task.createdAt == 0) {
                task.createdAt = System.currentTimeMillis();
            }
            dao.insert(task);
        });
    }

    public void update(Task task) {
        AppExecutors.diskIO().execute(() -> dao.update(task));
    }

    public void delete(Task task) {
        AppExecutors.diskIO().execute(() -> dao.delete(task));
    }

    public void markDone(long[] ids) {
        long now = System.currentTimeMillis();
        AppExecutors.diskIO().execute(() -> dao.markDone(ids, now));
    }

    public void markOpen(long id) {
        AppExecutors.diskIO().execute(() -> dao.markOpen(id));
    }
}
