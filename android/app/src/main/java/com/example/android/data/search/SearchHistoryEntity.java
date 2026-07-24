package com.example.android.data.search;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(
    tableName = "search_history",
    primaryKeys = {"query", "searchType"}
)
public final class SearchHistoryEntity {
    @NonNull
    private final String query;
    @NonNull
    private final String searchType;
    private final long searchedAtMillis;

    public SearchHistoryEntity(
        @NonNull String query,
        @NonNull String searchType,
        long searchedAtMillis
    ) {
        this.query = query;
        this.searchType = searchType;
        this.searchedAtMillis = searchedAtMillis;
    }

    @NonNull
    public String getQuery() {
        return query;
    }

    @NonNull
    public String getSearchType() {
        return searchType;
    }

    public long getSearchedAtMillis() {
        return searchedAtMillis;
    }
}
