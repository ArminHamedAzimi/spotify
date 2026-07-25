package com.example.android.data.chat;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface ChatMessageDao {
    @Query(
        "SELECT * FROM chat_messages " +
        "WHERE ownerUserId = :ownerUserId AND otherUserId = :otherUserId " +
        "ORDER BY createdAt ASC"
    )
    Flow<List<ChatMessageEntity>> observe(String ownerUserId, String otherUserId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ChatMessageEntity message);

    /** Skips if a server-confirmed row for this client id already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertOptimistic(ChatMessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ChatMessageEntity> messages);

    @Query(
        "UPDATE chat_messages SET status = :status " +
        "WHERE ownerUserId = :ownerUserId AND serverId = :serverId"
    )
    void updateStatus(String ownerUserId, String serverId, String status);
}
