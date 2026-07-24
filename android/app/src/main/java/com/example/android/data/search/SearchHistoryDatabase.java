package com.example.android.data.search;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {SearchHistoryEntity.class},
    version = 1,
    exportSchema = false
)
public abstract class SearchHistoryDatabase extends RoomDatabase {
    public abstract SearchHistoryDao historyDao();
}
