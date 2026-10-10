package com.sharma.nearlist.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.sharma.nearlist.data.dao.ActivityEventDao;
import com.sharma.nearlist.data.dao.PlaceDao;
import com.sharma.nearlist.data.dao.TaskDao;
import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.entity.Task;

@Database(entities = {Place.class, Task.class, ActivityEvent.class}, version = 1, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "nearlist.db";

    private static volatile AppDatabase instance;

    public abstract PlaceDao placeDao();

    public abstract TaskDao taskDao();

    public abstract ActivityEventDao activityEventDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DB_NAME)
                            // Prototype: schema changes wipe the database instead of needing migrations.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
