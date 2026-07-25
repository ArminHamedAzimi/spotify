package com.example.android.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.PagingData
import com.example.android.data.chat.ChatMessageEntity
import com.example.android.data.chat.ChatRepository
import com.example.android.data.remote.PublicProfileDto
import com.example.android.domain.home.Song
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val otherUser: PublicProfileDto? = null,
    val currentUserId: String? = null,
    val draft: String = "",
    val messages: List<ChatMessageEntity> = emptyList(),
    val isTyping: Boolean = false,
    val connected: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()
    private val activeUserId = MutableStateFlow<String?>(null)
    val conversations = activeUserId.flatMapLatest { userId ->
        if (userId == null) flowOf(PagingData.empty()) else repository.conversations()
    }.cachedIn(viewModelScope)
    private var messagesJob: Job? = null
    private var typingJob: Job? = null
    private val readMessageIds = mutableSetOf<String>()
    private var pendingShare: PendingSongShare? = null

    init {
        viewModelScope.launch {
            combine(repository.isTyping, repository.connected) { typing, connected ->
                typing to connected
            }.collect { (typing, connected) ->
                _state.update { it.copy(isTyping = typing, connected = connected) }
                if (connected) flushPendingShare()
            }
        }
    }

    fun setCurrentUser(userId: String?) {
        if (activeUserId.value == userId) return
        repository.switchAccount(userId)
        messagesJob?.cancel()
        typingJob?.cancel()
        readMessageIds.clear()
        pendingShare = null
        activeUserId.value = userId
        _state.value = ChatUiState(currentUserId = userId)
    }

    fun open(profile: PublicProfileDto) {
        val ownId = _state.value.currentUserId ?: return
        _state.update { ChatUiState(otherUser = profile, currentUserId = ownId) }
        repository.connect(profile.id, ownId)
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            runCatching { repository.sync(ownId, profile.id) }
            repository.messages(ownId, profile.id).collect { messages ->
                _state.update { it.copy(messages = messages) }
                messages.asSequence()
                    .filter { it.senderId != ownId && it.status != "read" }
                    .mapNotNull(ChatMessageEntity::serverId)
                    .filter(readMessageIds::add)
                    .forEach(repository::markRead)
            }
        }
    }

    fun openAndShareSong(profile: PublicProfileDto, songId: String, preview: Song? = null) {
        if (_state.value.currentUserId == null) return
        pendingShare = PendingSongShare(songId, preview)
        open(profile)
        flushPendingShare()
    }

    fun setDraft(value: String) {
        _state.update { it.copy(draft = value) }
        repository.setTyping(value.isNotBlank())
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(TYPING_IDLE_MILLIS)
            repository.setTyping(false)
        }
    }

    fun send() {
        val body = _state.value.draft
        if (body.isBlank()) return
        repository.sendText(body)
        repository.setTyping(false)
        _state.update { it.copy(draft = "") }
    }

    fun shareSong(songId: String, preview: Song? = null) {
        if (_state.value.otherUser == null) return
        pendingShare = PendingSongShare(songId, preview)
        flushPendingShare()
    }

    fun close() {
        repository.setTyping(false)
        repository.disconnect()
        repository.refreshConversations()
        messagesJob?.cancel()
        pendingShare = null
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private fun flushPendingShare() {
        val share = pendingShare ?: return
        if (!repository.isConnected()) return
        if (repository.sendSong(share.songId, share.preview, clientMessageId = share.clientMessageId)) {
            pendingShare = null
        }
    }

    private data class PendingSongShare(
        val songId: String,
        val preview: Song?,
        val clientMessageId: String = UUID.randomUUID().toString()
    )

    private companion object {
        const val TYPING_IDLE_MILLIS = 1_200L
    }
}
