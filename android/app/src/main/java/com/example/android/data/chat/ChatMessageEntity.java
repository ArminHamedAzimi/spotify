package com.example.android.data.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;

@Entity(
    tableName = "chat_messages",
    primaryKeys = {"ownerUserId", "clientMessageId"}
)
public final class ChatMessageEntity {
    @NonNull public final String ownerUserId;
    @NonNull public final String clientMessageId;
    @Nullable public final String serverId;
    @NonNull public final String otherUserId;
    @NonNull public final String senderId;
    @NonNull public final String senderName;
    @NonNull public final String messageType;
    @NonNull public final String body;
    @NonNull public final String status;
    @NonNull public final String createdAt;
    @Nullable public final String songId;
    @Nullable public final String songTitle;
    @Nullable public final String songArtist;
    @Nullable public final String songCoverUrl;
    @Nullable public final String songAudioUrl;
    @Nullable public final String songDuration;

    public ChatMessageEntity(
        @NonNull String ownerUserId, @NonNull String clientMessageId,
        @Nullable String serverId,
        @NonNull String otherUserId, @NonNull String senderId,
        @NonNull String senderName, @NonNull String messageType,
        @NonNull String body, @NonNull String status, @NonNull String createdAt,
        @Nullable String songId, @Nullable String songTitle,
        @Nullable String songArtist, @Nullable String songCoverUrl,
        @Nullable String songAudioUrl, @Nullable String songDuration
    ) {
        this.ownerUserId = ownerUserId;
        this.clientMessageId = clientMessageId;
        this.serverId = serverId;
        this.otherUserId = otherUserId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.messageType = messageType;
        this.body = body;
        this.status = status;
        this.createdAt = createdAt;
        this.songId = songId;
        this.songTitle = songTitle;
        this.songArtist = songArtist;
        this.songCoverUrl = songCoverUrl;
        this.songAudioUrl = songAudioUrl;
        this.songDuration = songDuration;
    }
}
