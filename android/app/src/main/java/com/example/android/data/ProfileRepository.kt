package com.example.android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.example.android.BuildConfig
import com.example.android.data.remote.LoginRequest
import com.example.android.data.remote.RefreshRequest
import com.example.android.data.remote.RegisterRequest
import com.example.android.data.remote.SpotifyApi
import com.example.android.data.remote.SubscriptionRequest
import com.example.android.data.remote.SubscriptionResponse
import com.example.android.data.remote.UserDto
import com.example.android.data.session.TokenStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

class ProfileRepository(private val context: Context) {
    private val tokenStore = TokenStore(context)
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(
            ChuckerInterceptor.Builder(context)
                .redactHeaders("Authorization")
                .build()
        )
        .build()
    private val api = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyApi::class.java)

    suspend fun restoreUser(): UserDto? {
        if (tokenStore.accessToken == null) return null
        return authenticated { authorization -> api.currentUser(authorization) }
    }

    suspend fun login(email: String, password: String): UserDto {
        val tokens = api.login(LoginRequest(email.trim(), password))
        tokenStore.save(tokens.access, tokens.refresh)
        return api.currentUser(bearer(tokens.access))
    }

    suspend fun register(name: String, email: String, password: String): UserDto {
        api.register(RegisterRequest(name.trim(), email.trim(), password))
        return login(email, password)
    }

    suspend fun uploadAvatar(avatarUri: Uri): String {
        return authenticated { authorization ->
            api.uploadAvatar(authorization, avatarPart(avatarUri)).avatarUrl
        }
    }

    suspend fun saveProfile(user: UserDto, name: String, avatarUrl: String?): UserDto {
        val changes = mutableMapOf("name" to name.trim())
        avatarUrl?.let { changes["avatar_url"] = it }
        return authenticated { authorization ->
            api.updateUser(authorization, user.id, changes)
        }
    }

    suspend fun addSubscription(months: Int): SubscriptionResponse {
        return authenticated { authorization ->
            api.addSubscription(authorization, SubscriptionRequest(months))
        }
    }

    fun logout() = tokenStore.clear()

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val access = requireNotNull(tokenStore.accessToken)
        return try {
            block(bearer(access))
        } catch (error: HttpException) {
            if (error.code() != UNAUTHORIZED) throw error
            val refreshToken = tokenStore.refreshToken ?: throw error
            val refreshed = api.refresh(RefreshRequest(refreshToken))
            tokenStore.save(refreshed.access)
            block(bearer(refreshed.access))
        }
    }

    private fun avatarPart(uri: Uri): MultipartBody.Part {
        val resolver = context.contentResolver
        val displayName = avatarDisplayName(uri)
        val mediaType = normalizedImageType(
            reportedType = resolver.getType(uri),
            displayName = displayName
        )
        val bytes = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
        require(bytes.size <= MAX_AVATAR_BYTES)
        return MultipartBody.Part.createFormData(
            "avatar",
            avatarFileName(displayName, mediaType),
            bytes.toRequestBody(mediaType.toMediaType())
        )
    }

    private fun avatarDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    private fun normalizedImageType(reportedType: String?, displayName: String?): String {
        val normalizedReportedType = when (reportedType?.lowercase(Locale.ROOT)) {
            "image/jpg", "image/pjpeg" -> "image/jpeg"
            else -> reportedType?.lowercase(Locale.ROOT)
        }
        if (normalizedReportedType in SUPPORTED_IMAGE_TYPES) {
            return requireNotNull(normalizedReportedType)
        }

        val extension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
        return requireNotNull(MIME_TYPES_BY_EXTENSION[extension])
    }

    private fun avatarFileName(displayName: String?, mediaType: String): String {
        val expectedExtension = requireNotNull(IMAGE_EXTENSIONS[mediaType])
        val suppliedExtension = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)

        return if (displayName != null && suppliedExtension in requireNotNull(VALID_EXTENSIONS[mediaType])) {
            displayName.substringAfterLast('/')
        } else {
            "$AVATAR_FILE_NAME.$expectedExtension"
        }
    }

    private fun bearer(token: String) = "Bearer $token"

    private companion object {
        const val UNAUTHORIZED = 401
        const val MAX_AVATAR_BYTES = 5 * 1024 * 1024
        const val AVATAR_FILE_NAME = "avatar"
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        val VALID_EXTENSIONS = mapOf(
            "image/jpeg" to setOf("jpg", "jpeg"),
            "image/png" to setOf("png"),
            "image/webp" to setOf("webp")
        )
        val IMAGE_EXTENSIONS = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp"
        )
        val MIME_TYPES_BY_EXTENSION = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "webp" to "image/webp"
        )
    }
}
