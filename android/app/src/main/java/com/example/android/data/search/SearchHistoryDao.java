package com.example.android.data.search;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

@Dao
public interface SearchHistoryDao {
    @Query(
        "SELECT * FROM search_history " +
        "ORDER BY searchedAtMillis DESC LIMIT :limit"
    )
    Flow<List<SearchHistoryEntity>> observeRecent(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(SearchHistoryEntity item);

    @Delete
    void remove(SearchHistoryEntity item);
}
