package com.example.android.data.chat

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val CHAT_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                clientMessageId TEXT NOT NULL,
                serverId TEXT,
                otherUserId TEXT NOT NULL,
                senderId TEXT NOT NULL,
                senderName TEXT NOT NULL,
                messageType TEXT NOT NULL,
                body TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                songId TEXT,
                songTitle TEXT,
                songArtist TEXT,
                songCoverUrl TEXT,
                songAudioUrl TEXT,
                songDuration TEXT,
                PRIMARY KEY(clientMessageId)
            )
            """.trimIndent()
        )
    }
}

val CHAT_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Version 2 chat rows were not attributable to an authenticated account.
        // REST synchronization restores them safely for the next signed-in owner.
        db.execSQL("DROP TABLE IF EXISTS chat_messages")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                ownerUserId TEXT NOT NULL,
                clientMessageId TEXT NOT NULL,
                serverId TEXT,
                otherUserId TEXT NOT NULL,
                senderId TEXT NOT NULL,
                senderName TEXT NOT NULL,
                messageType TEXT NOT NULL,
                body TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                songId TEXT,
                songTitle TEXT,
                songArtist TEXT,
                songCoverUrl TEXT,
                songAudioUrl TEXT,
                songDuration TEXT,
                PRIMARY KEY(ownerUserId, clientMessageId)
            )
            """.trimIndent()
        )
    }
}
