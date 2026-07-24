package com.example.android.data.search;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
    entities = {SearchHistoryEntity.class, com.example.android.data.chat.ChatMessageEntity.class},
    version = 3,
    exportSchema = false
)
public abstract class SearchHistoryDatabase extends RoomDatabase {
    public abstract SearchHistoryDao historyDao();
    public abstract com.example.android.data.chat.ChatMessageDao chatMessageDao();
}
