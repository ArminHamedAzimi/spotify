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
    val songs: List<String>,
    @SerializedName("follower_count") val followerCount: Int
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
