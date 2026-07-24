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
