package com.example.android.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface SpotifyApi {
    @POST("users/")
    suspend fun register(@Body request: RegisterRequest): UserDto

    @POST("auth/token/")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("auth/token/refresh/")
    suspend fun refresh(@Body request: RefreshRequest): AccessTokenResponse

    @GET("users/me/")
    suspend fun currentUser(@Header("Authorization") authorization: String): UserDto

    @PATCH("users/{id}/")
    suspend fun updateUser(
        @Header("Authorization") authorization: String,
        @Path("id") userId: String,
        @Body changes: Map<String, String>
    ): UserDto

    @Multipart
    @POST("users/avatar/")
    suspend fun uploadAvatar(
        @Header("Authorization") authorization: String,
        @Part avatar: MultipartBody.Part
    ): AvatarUploadResponse

    @POST("users/subscription/")
    suspend fun addSubscription(
        @Header("Authorization") authorization: String,
        @Body request: SubscriptionRequest
    ): SubscriptionResponse
}
