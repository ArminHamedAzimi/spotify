package com.example.android.data.chat

import com.example.android.BuildConfig
import com.example.android.data.remote.*
import com.example.android.data.session.TokenStore
import com.example.android.domain.home.Song
import androidx.paging.*
import kotlinx.coroutines.flow.Flow
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArraySet

class ChatRepository(
    private val tokenStore: TokenStore,
    private val api: SpotifyApi,
    private val client: OkHttpClient,
    private val dao: ChatMessageDao,
    private val gson: Gson = Gson()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var otherUserId: String? = null
    private var currentUserId: String? = null
    private val _isTyping = MutableStateFlow(false)
    val isTyping = _isTyping.asStateFlow()
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val conversationSources = CopyOnWriteArraySet<ConversationsPagingSource>()

    fun messages(ownerUserId: String, userId: String) = dao.observe(ownerUserId, userId)
    fun conversations(): Flow<PagingData<ConversationDto>> = Pager(
        PagingConfig(CONVERSATION_PAGE_SIZE, enablePlaceholders = false)
    ) {
        ConversationsPagingSource { page, size ->
            authenticated { api.conversations(it, page, size) }
        }.also { source ->
            conversationSources += source
            source.registerInvalidatedCallback { conversationSources -= source }
        }
    }.flow

    fun refreshConversations() {
        conversationSources.toList().forEach(ConversationsPagingSource::invalidate)
    }

    suspend fun sync(ownerUserId: String, userId: String, page: Int = 1) {
        val response = authenticated { api.chatMessages(it, userId, page, MESSAGE_PAGE_SIZE) }
        withContext(Dispatchers.IO) {
            dao.upsertAll(response.results.map { it.toEntity(ownerUserId, userId) })
        }
    }

    fun switchAccount(ownerUserId: String?) {
        if (currentUserId == ownerUserId) return
        disconnect()
        currentUserId = ownerUserId
    }

    fun connect(userId: String, ownUserId: String) {
        if (otherUserId == userId && socket != null) return
        disconnect()
        otherUserId = userId
        currentUserId = ownUserId
        val access = tokenStore.accessToken ?: return
        val base = BuildConfig.API_BASE_URL
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
            .substringBefore("/api/")
        val request = Request.Builder()
            .url("$base/ws/chat/$userId/")
            .header("Authorization", "Bearer $access")
            .build()
        socket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        socket?.close(NORMAL_CLOSE_CODE, null)
        socket = null
        otherUserId = null
        _connected.value = false
        _isTyping.value = false
    }

    fun sendText(body: String) {
        val target = otherUserId ?: return
        val sender = currentUserId ?: return
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        val clientId = UUID.randomUUID().toString()
        scope.launch {
            dao.upsert(
                ChatMessageEntity(
                    sender, clientId, null, target, sender, "", "text", trimmed,
                    "sending", now(), null, null, null, null, null, null
                )
            )
        }
        send(
            mapOf(
                "type" to "message.send",
                "client_message_id" to clientId,
                "message_type" to "text",
                "body" to trimmed
            )
        )
    }

    fun sendSong(songId: String, body: String = "") {
        send(
            mapOf(
                "type" to "message.send",
                "client_message_id" to UUID.randomUUID().toString(),
                "message_type" to "song",
                "song_id" to songId,
                "body" to body
            )
        )
    }

    fun setTyping(typing: Boolean) = send(mapOf("type" to "typing", "is_typing" to typing))

    fun markRead(messageId: String) {
        if (!send(mapOf("type" to "message.read", "message_id" to messageId))) {
            scope.launch {
                runCatching { authenticated { api.markChatMessageRead(it, messageId) } }
                    .onSuccess { refreshConversations() }
            }
        }
    }

    private fun send(payload: Map<String, Any>): Boolean =
        socket?.send(gson.toJson(payload)) == true

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connected.value = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val event = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
                ?: return
            when (event["type"]?.asString) {
                "message.created" -> {
                    val message = gson.fromJson(event["message"], ChatMessageDto::class.java)
                    val target = otherUserId ?: return
                    val owner = currentUserId ?: return
                    scope.launch {
                        dao.upsert(message.toEntity(owner, target))
                        if (message.sender.id != currentUserId) markRead(message.id)
                    }
                }
                "message.receipt" -> {
                    val id = event["message_id"]?.asString ?: return
                    val status = event["status"]?.asString ?: return
                    val owner = currentUserId ?: return
                    scope.launch {
                        dao.updateStatus(owner, id, status)
                        if (status == "read") refreshConversations()
                    }
                }
                "typing" -> {
                    if (event["user_id"]?.asString == otherUserId) {
                        _isTyping.value = event["is_typing"]?.asBoolean == true
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            _connected.value = false
            _isTyping.value = false
            reconnectIfNeeded()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
            _connected.value = false
            _isTyping.value = false
            reconnectIfNeeded()
        }
    }

    private fun reconnectIfNeeded() {
        val target = otherUserId ?: return
        val ownId = currentUserId ?: return
        scope.launch {
            delay(RECONNECT_DELAY_MILLIS)
            if (otherUserId == target && socket == null) connect(target, ownId)
        }
    }

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val access = requireNotNull(tokenStore.accessToken)
        return try {
            block("Bearer $access")
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            val token = api.refresh(RefreshRequest(requireNotNull(tokenStore.refreshToken)))
            tokenStore.save(token.access)
            block("Bearer ${token.access}")
        }
    }

    private fun ChatMessageDto.toEntity(owner: String, target: String) = ChatMessageEntity(
        owner, clientMessageId, id, target, sender.id, sender.name, messageType, body,
        status, createdAt, song?.id, song?.title, song?.artist?.name,
        song?.coverImageUrl, song?.audioUrl, song?.duration
    )

    private fun now(): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    private companion object {
        const val MESSAGE_PAGE_SIZE = 50
        const val NORMAL_CLOSE_CODE = 1000
        const val CONVERSATION_PAGE_SIZE = 10
        const val RECONNECT_DELAY_MILLIS = 2_000L
    }
}

private class ConversationsPagingSource(
    private val loadPage: suspend (Int, Int) -> PaginatedResponse<ConversationDto>
) : PagingSource<Int, ConversationDto>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ConversationDto> {
        val page = params.key ?: 1
        return runCatching {
            val response = loadPage(page, params.loadSize)
            LoadResult.Page(
                response.results,
                if (page == 1) null else page - 1,
                if (response.next == null) null else page + 1
            )
        }.getOrElse { LoadResult.Error(it) }
    }

    override fun getRefreshKey(state: PagingState<Int, ConversationDto>): Int? =
        state.anchorPosition?.let(state::closestPageToPosition)?.let {
            it.prevKey?.plus(1) ?: it.nextKey?.minus(1)
        }
}
