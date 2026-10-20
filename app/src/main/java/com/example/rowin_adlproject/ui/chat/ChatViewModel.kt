package com.example.rowin_adlproject.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rowin_adlproject.ai.ChatService
import com.example.rowin_adlproject.data.AlertsEngine
import com.example.rowin_adlproject.data.ChatMessage
import com.example.rowin_adlproject.data.SessionMemory
import com.example.rowin_adlproject.ui.rowinContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Chat state for practical #8.
 *
 * The stylist reads the app-wide [SessionMemory] (profile + every clothing
 * item + every saved outfit), which is loaded eagerly on login, so it always
 * reasons over the whole closet instead of reporting it as empty.
 */
class ChatViewModel(
    /** Shared, app-lifetime snapshot owned by the AppContainer. */
    private val memory: StateFlow<SessionMemory>,
    userName: String?,
) : ViewModel() {

    private val chatService = ChatService()

    private val _messages = MutableStateFlow(AlertsEngine.welcomeMessages(userName))
    val messages: StateFlow<List<ChatMessage>> = _messages

    val input = MutableStateFlow("")

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    fun send() {
        val text = input.value.trim()
        if (text.isEmpty() || _thinking.value) return
        input.value = ""
        _messages.value = _messages.value + ChatMessage(role = "user", text = text)
        _thinking.value = true
        viewModelScope.launch {
            // Fresh read at send time: Firestore may have streamed new pieces in.
            val snapshot = memory.value
            val result = chatService.reply(_messages.value, snapshot)
            val reply = result.getOrElse {
                "I couldn't reach the AI service. Check your connection or Groq API key and try again."
            }
            _messages.value = _messages.value + ChatMessage(role = "assistant", text = reply)
            _thinking.value = false
        }
    }

    class Factory(
        private val context: Context,
        private val userName: String?,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = context.rowinContainer()
            return ChatViewModel(container.sessionMemory, userName) as T
        }
    }
}
