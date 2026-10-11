package com.sharma.nearlist.data.dao;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.sharma.nearlist.data.db.AppDatabase;
import com.sharma.nearlist.data.entity.Place;
import com.sharma.nearlist.data.model.Category;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PlaceDaoTest {

    private AppDatabase db;
    private PlaceDao dao;

    @Before
    public void createDb() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = db.placeDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void clampQuietUntil_shortensLaterValues_andKeepsEarlierOnes() {
        long cooling = dao.insert(place("Keells", 50_000L));
        long shortQuiet = dao.insert(place("Ceypetco", 15_000L));
        long idle = dao.insert(place("Healthguard", 0L));

        dao.clampQuietUntil(20_000L);

        assertEquals(20_000L, dao.getByIdSync(cooling).quietUntil);
        assertEquals(15_000L, dao.getByIdSync(shortQuiet).quietUntil);
        assertEquals(0L, dao.getByIdSync(idle).quietUntil);
    }

    private static Place place(String name, long quietUntil) {
        Place place = new Place();
        place.name = name;
        place.category = Category.GROCERY;
        place.radiusM = 150;
        place.quietUntil = quietUntil;
        return place;
    }
}
