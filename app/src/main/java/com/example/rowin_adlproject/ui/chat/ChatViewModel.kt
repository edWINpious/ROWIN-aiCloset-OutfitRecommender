package com.example.rowin_adlproject.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rowin_adlproject.ai.ChatService
import com.example.rowin_adlproject.data.AlertsEngine
import com.example.rowin_adlproject.data.ChatMessage
import com.example.rowin_adlproject.data.WardrobeRepository
import com.example.rowin_adlproject.ui.rowinContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Chat state for practical #8. The wardrobe is observed live so the stylist
 * always reasons over the user's current closet.
 */
class ChatViewModel(
    repository: WardrobeRepository,
    private val userId: Long,
    userName: String?,
) : ViewModel() {

    private val chatService = ChatService()

    private val _messages = MutableStateFlow(AlertsEngine.welcomeMessages(userName))
    val messages: StateFlow<List<ChatMessage>> = _messages

    val input = MutableStateFlow("")

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    private val wardrobe = repository.observeClothing(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun send() {
        val text = input.value.trim()
        if (text.isEmpty() || _thinking.value) return
        input.value = ""
        _messages.value = _messages.value + ChatMessage(role = "user", text = text)
        _thinking.value = true
        viewModelScope.launch {
            val result = chatService.reply(_messages.value, wardrobe.value)
            val reply = result.getOrElse {
                "I couldn't reach the AI service. Check your connection or Groq API key and try again."
            }
            _messages.value = _messages.value + ChatMessage(role = "assistant", text = reply)
            _thinking.value = false
        }
    }

    class Factory(
        private val context: Context,
        private val userId: Long,
        private val userName: String?,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val container = context.rowinContainer()
            return ChatViewModel(container.repository, userId, userName) as T
        }
    }
}
