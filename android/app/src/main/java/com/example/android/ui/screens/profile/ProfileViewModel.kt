package com.example.android.ui.screens.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.R
import com.example.android.data.ProfileRepository
import com.example.android.data.remote.UserDto
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserDto? = null,
    val isLoading: Boolean = true,
    val isUploadingAvatar: Boolean = false,
    val pendingAvatarUrl: String? = null,
    val saveSuccessCount: Int = 0,
    val subscriptionSuccessCount: Int = 0,
    val isUpdatingSubscription: Boolean = false,
    val errorRes: Int? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProfileRepository(application)

    var uiState = androidx.compose.runtime.mutableStateOf(ProfileUiState())
        private set

    init {
        restoreSession()
    }

    fun login(email: String, password: String) = perform {
        repository.login(email, password)
    }

    fun register(name: String, email: String, password: String) = perform {
        repository.register(name, email, password)
    }

    fun uploadAvatar(avatarUri: Uri) {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(
                isUploadingAvatar = true,
                pendingAvatarUrl = null,
                errorRes = null
            )
            uiState.value = try {
                uiState.value.copy(
                    isUploadingAvatar = false,
                    pendingAvatarUrl = repository.uploadAvatar(avatarUri)
                )
            } catch (_: IllegalArgumentException) {
                uiState.value.copy(
                    isUploadingAvatar = false,
                    errorRes = R.string.avatar_validation_error
                )
            } catch (_: Exception) {
                uiState.value.copy(
                    isUploadingAvatar = false,
                    errorRes = R.string.avatar_upload_failed
                )
            }
        }
    }

    fun saveProfile(name: String) {
        val currentUser = uiState.value.user ?: return
        viewModelScope.launch {
            val currentState = uiState.value
            uiState.value = currentState.copy(isLoading = true, errorRes = null)
            uiState.value = try {
                val savedUser = repository.saveProfile(
                    currentUser,
                    name,
                    currentState.pendingAvatarUrl
                )
                currentState.copy(
                    user = savedUser,
                    isLoading = false,
                    pendingAvatarUrl = null,
                    saveSuccessCount = currentState.saveSuccessCount + 1,
                    errorRes = null
                )
            } catch (_: Exception) {
                currentState.copy(
                    isLoading = false,
                    errorRes = R.string.request_failed
                )
            }
        }
    }

    fun logout() {
        repository.logout()
        uiState.value = ProfileUiState(isLoading = false)
    }

    fun addSubscription(months: Int) {
        val currentUser = uiState.value.user ?: return
        viewModelScope.launch {
            val currentState = uiState.value
            uiState.value = currentState.copy(
                isUpdatingSubscription = true,
                errorRes = null
            )
            uiState.value = try {
                val subscription = repository.addSubscription(months)
                currentState.copy(
                    user = currentUser.copy(
                        premiumExpiresAt = subscription.premiumExpiresAt,
                        hasActivePremium = subscription.hasActivePremium
                    ),
                    isUpdatingSubscription = false,
                    subscriptionSuccessCount = currentState.subscriptionSuccessCount + 1,
                    errorRes = null
                )
            } catch (_: Exception) {
                currentState.copy(
                    isUpdatingSubscription = false,
                    errorRes = R.string.subscription_failed
                )
            }
        }
    }

    fun clearError() {
        uiState.value = uiState.value.copy(errorRes = null)
    }

    private fun restoreSession() {
        viewModelScope.launch {
            uiState.value = try {
                ProfileUiState(user = repository.restoreUser(), isLoading = false)
            } catch (_: Exception) {
                repository.logout()
                ProfileUiState(isLoading = false, errorRes = R.string.session_restore_error)
            }
        }
    }

    private fun perform(request: suspend () -> UserDto) {
        viewModelScope.launch {
            uiState.value = uiState.value.copy(isLoading = true, errorRes = null)
            uiState.value = try {
                ProfileUiState(user = request(), isLoading = false)
            } catch (_: IllegalArgumentException) {
                uiState.value.copy(
                    isLoading = false,
                    errorRes = R.string.avatar_validation_error
                )
            } catch (_: Exception) {
                uiState.value.copy(
                    isLoading = false,
                    errorRes = R.string.request_failed
                )
            }
        }
    }
}
