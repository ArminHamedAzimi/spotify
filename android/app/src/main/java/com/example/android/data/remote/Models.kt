package com.example.android.data.remote

import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    @SerializedName("premium_expires_at") val premiumExpiresAt: String?,
    @SerializedName("has_active_premium") val hasActivePremium: Boolean,
    @SerializedName("avatar_url") val avatarUrl: String?
)

data class PublicProfileDto(
    val id: String,
    val name: String,
    @SerializedName("avatar_url") val avatarUrl: String?,
    @SerializedName("has_active_premium") val hasActivePremium: Boolean
)

data class UserFollowDto(
    val id: String,
    val follower: PublicProfileDto,
    val following: PublicProfileDto
)

data class PlaylistVisibilityRequest(
    @SerializedName("is_public") val isPublic: Boolean
)

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class TokenResponse(val refresh: String, val access: String)
data class RefreshRequest(val refresh: String)
data class AccessTokenResponse(val access: String)

data class AvatarUploadResponse(
    @SerializedName("avatar_url") val avatarUrl: String
)

data class SubscriptionRequest(val months: Int)

data class SubscriptionResponse(
    @SerializedName("months_added") val monthsAdded: Int,
    @SerializedName("premium_expires_at") val premiumExpiresAt: String,
    @SerializedName("has_active_premium") val hasActivePremium: Boolean
)

data class SongDto(
    val id: String,
    val title: String,
    val artist: UserDto,
    @SerializedName("cover_image_url") val coverImageUrl: String,
    @SerializedName("audio_url") val audioUrl: String,
    val duration: String?,
    @SerializedName("is_published") val isPublished: Boolean
)

data class PlaylistDto(
    val id: String,
    val owner: UserDto,
    val title: String,
    val description: String,
    @SerializedName("is_public") val isPublic: Boolean,
    @SerializedName("is_liked") val isLiked: Boolean,
    @SerializedName("song_count") val songCount: Int,
    @SerializedName("follower_count") val followerCount: Int
)

data class PaginatedResponse<T>(
    val count: Int,
    val next: String?,
    val previous: String?,
    val results: List<T>
)

data class CreatePlaylistRequest(
    val title: String,
    val description: String = "",
    @SerializedName("is_public") val isPublic: Boolean = false
)
data class PlaylistSongRequest(@SerializedName("song_id") val songId: String)
data class NextSongRequest(
    @SerializedName("song_id") val songId: String?,
    val shuffle: Boolean
)
data class RandomNextRequest(@SerializedName("song_id") val songId: String?)

data class ChatSongDto(
    val id: String,
    val title: String,
    val artist: PublicProfileDto,
    @SerializedName("cover_image_url") val coverImageUrl: String,
    @SerializedName("audio_url") val audioUrl: String,
    val duration: String?
)

data class ChatMessageDto(
    val id: String,
    @SerializedName("conversation_id") val conversationId: String,
    @SerializedName("client_message_id") val clientMessageId: String,
    val sender: PublicProfileDto,
    @SerializedName("message_type") val messageType: String,
    val body: String,
    val song: ChatSongDto?,
    val status: String,
    @SerializedName("delivered_at") val deliveredAt: String?,
    @SerializedName("read_at") val readAt: String?,
    @SerializedName("created_at") val createdAt: String
)

data class ConversationLastMessageDto(
    val id: String,
    @SerializedName("message_type") val messageType: String,
    val body: String,
    val status: String,
    val song: ChatSongDto?,
    @SerializedName("created_at") val createdAt: String
)

data class ConversationDto(
    val id: String,
    @SerializedName("other_user") val otherUser: PublicProfileDto,
    @SerializedName("last_message") val lastMessage: ConversationLastMessageDto?,
    @SerializedName("unread_count") val unreadCount: Int,
    @SerializedName("updated_at") val updatedAt: String
)
