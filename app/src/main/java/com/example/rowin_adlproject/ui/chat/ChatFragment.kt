package com.example.rowin_adlproject.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.ChatMessage
import com.example.rowin_adlproject.databinding.ScreenChatBinding
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.onTextChanged

/**
 * Practical #8 - AI chatbot: full-screen stylist chat grounded in the user's
 * real wardrobe. Assistant = white card left, user = brand green right.
 */
class ChatFragment : HostedFragment(R.layout.screen_chat) {

    private var _b: ScreenChatBinding? = null
    private val b get() = _b!!

    private lateinit var vm: ChatViewModel
    private lateinit var adapter: BubbleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenChatBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        vm = androidx.lifecycle.ViewModelProvider(
            this, Loaders.chat(requireContext(), host.rowin.user.value?.name),
        )[ChatViewModel::class.java]

        adapter = BubbleAdapter()
        b.chatMessages.layoutManager = LinearLayoutManager(requireContext())
        b.chatMessages.adapter = adapter

        b.chatBack.setOnClickListener { host.back() }
        b.chatInput.onTextChanged { vm.input.value = it }
        b.chatSend.setOnClickListener { sendCurrent() }
        b.chatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendCurrent()
                true
            } else false
        }

        vm.messages.collectWithViewLifecycle { messages ->
            adapter.submit(messages)
            if (messages.isNotEmpty()) {
                b.chatMessages.scrollToPosition(messages.size - 1)
            }
        }
        vm.input.collectWithViewLifecycle { input ->
            if (b.chatInput.text?.toString() != input) b.chatInput.setText(input)
        }
        vm.thinking.collectWithViewLifecycle { thinking ->
            b.chatStatus.text =
                if (thinking) getString(R.string.typing) else getString(R.string.ai_subtitle)
            Views.toggle(
                showViews = if (thinking) listOf(b.chatThinking) else emptyList(),
                hideViews = if (thinking) emptyList() else listOf(b.chatThinking),
            )
        }
    }

    private fun sendCurrent() {
        vm.send()
        b.chatInput.setText("")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private class BubbleAdapter : RecyclerView.Adapter<BubbleAdapter.Holder>() {

        private val messages = mutableListOf<ChatMessage>()

        fun submit(newMessages: List<ChatMessage>) {
            messages.clear()
            messages += newMessages
            notifyDataSetChanged()
        }

        class Holder(val root: FrameLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_bubble, parent, false)
                as FrameLayout)

        override fun getItemCount(): Int = messages.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val message = messages[position]
            val isUser = message.role == "user"
            val context = holder.root.context
            val bubble = holder.root.findViewById<TextView>(R.id.bubble_text)
            bubble.text = message.text
            bubble.background = ContextCompat.getDrawable(
                context, if (isUser) R.drawable.bg_bubble_user else R.drawable.bg_bubble_ai,
            )
            bubble.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isUser) R.color.rowin_on_primary_container else R.color.rowin_on_surface,
                ),
            )
            (bubble.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.gravity = if (isUser) android.view.Gravity.END else android.view.Gravity.START
                bubble.layoutParams = params
            }
        }
    }
}
