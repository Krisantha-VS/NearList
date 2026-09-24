package com.sharma.nearlist.data.db;

import androidx.room.TypeConverter;

import com.sharma.nearlist.data.model.Category;

/** Stores {@link Category} as its enum name ("GROCERY"). */
public final class Converters {

    private Converters() {
    }

    @TypeConverter
    public static String fromCategory(Category category) {
        return category == null ? null : category.key();
    }

    @TypeConverter
    public static Category toCategory(String key) {
        return Category.fromKey(key);
    }
}
