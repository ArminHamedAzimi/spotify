package com.example.android.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyApi {
    @POST("users/")
    suspend fun register(@Body request: RegisterRequest): UserDto

    @POST("auth/token/")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("auth/token/refresh/")
    suspend fun refresh(@Body request: RefreshRequest): AccessTokenResponse

    @GET("users/me/")
    suspend fun currentUser(@Header("Authorization") authorization: String): UserDto

    @GET("users/search/")
    suspend fun searchUsers(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int
    ): PaginatedResponse<PublicProfileDto>

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

    @GET("songs/recent/")
    suspend fun recentSongs(
        @Header("Authorization") authorization: String
    ): List<SongDto>

    @GET("songs/search/")
    suspend fun searchSongs(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int
    ): PaginatedResponse<SongDto>

    @GET("playlists/me/")
    suspend fun myPlaylists(
        @Header("Authorization") authorization: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int
    ): PaginatedResponse<PlaylistDto>

    @POST("playlists/")
    suspend fun createPlaylist(
        @Header("Authorization") authorization: String,
        @Body request: CreatePlaylistRequest
    ): PlaylistDto

    @GET("playlists/{id}/")
    suspend fun playlist(
        @Header("Authorization") authorization: String,
        @Path("id") playlistId: String
    ): PlaylistDto

    @GET("playlists/{id}/songs/")
    suspend fun playlistSongs(
        @Header("Authorization") authorization: String,
        @Path("id") playlistId: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int
    ): PaginatedResponse<SongDto>

    @GET("songs/{id}/")
    suspend fun song(
        @Header("Authorization") authorization: String,
        @Path("id") songId: String
    ): SongDto

    @POST("playlists/{id}/songs/")
    suspend fun addSongToPlaylist(
        @Header("Authorization") authorization: String,
        @Path("id") playlistId: String,
        @Body request: PlaylistSongRequest
    ): SongDto

    @DELETE("playlists/{playlist_id}/songs/{song_id}/")
    suspend fun removeSongFromPlaylist(
        @Header("Authorization") authorization: String,
        @Path("playlist_id") playlistId: String,
        @Path("song_id") songId: String
    )

    @POST("playlists/{id}/next-song/")
    suspend fun playlistNextSong(
        @Header("Authorization") authorization: String,
        @Path("id") playlistId: String,
        @Body request: NextSongRequest
    ): SongDto

    @POST("songs/random-next/")
    suspend fun randomNextSong(
        @Header("Authorization") authorization: String,
        @Body request: RandomNextRequest
    ): SongDto
}
