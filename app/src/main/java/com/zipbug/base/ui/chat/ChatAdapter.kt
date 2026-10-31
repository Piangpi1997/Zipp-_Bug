package com.zipbug.base.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.zipbug.base.R
import com.zipbug.base.data.ChatMessageEntity
import com.zipbug.base.databinding.ItemChatMessageBinding

class ChatAdapter(
    private val onOpenWorkspaceWithSnippet: (String, String) -> Unit,
    private val onConfigureKey: (String) -> Unit,
    private val onRetry: ((ChatMessageEntity) -> Unit)? = null
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = mutableListOf<ChatMessageEntity>()

    fun submitList(list: List<ChatMessageEntity>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    fun addMessage(msg: ChatMessageEntity) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(content: String, status: String = "sent", codeSnippet: String? = null, codeLanguage: String? = null) {
        if (messages.isNotEmpty()) {
            val lastIdx = messages.size - 1
            val updated = messages[lastIdx].copy(
                content = content,
                status = status,
                codeSnippet = codeSnippet ?: messages[lastIdx].codeSnippet,
                codeLanguage = codeLanguage ?: messages[lastIdx].codeLanguage
            )
            messages[lastIdx] = updated
            notifyItemChanged(lastIdx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatMessageEntity) {
            val context = itemView.context

            if (item.sender == "user") {
                binding.layoutUserMessage.visibility = View.VISIBLE
                binding.layoutAssistantMessage.visibility = View.GONE
                binding.layoutThinking.visibility = View.GONE

                binding.tvUserContent.text = item.content
                if (!item.toolAttachment.isNullOrBlank()) {
                    binding.tvUserAttachment.visibility = View.VISIBLE
                    binding.tvUserAttachment.text = item.toolAttachment
                } else {
                    binding.tvUserAttachment.visibility = View.GONE
                }
            } else {
                binding.layoutUserMessage.visibility = View.GONE
                binding.layoutAssistantMessage.visibility = View.VISIBLE

                if (item.status == "generating" && item.content.isBlank()) {
                    binding.layoutThinking.visibility = View.VISIBLE
                    binding.tvAssistantContent.visibility = View.GONE
                    binding.layoutCodeBlock.visibility = View.GONE
                } else {
                    binding.layoutThinking.visibility = View.GONE
                    binding.tvAssistantContent.visibility = View.VISIBLE

                    val (plainText, extractedCode, lang) = parseMarkdownCodeBlocks(item.content, item.codeSnippet, item.codeLanguage)
                    binding.tvAssistantContent.text = plainText

                    if (!extractedCode.isNullOrBlank()) {
                        binding.layoutCodeBlock.visibility = View.VISIBLE
                        binding.tvCodeLanguage.text = (lang ?: "CODE").uppercase()
                        binding.tvCodeSnippet.text = extractedCode

                        binding.btnCopyCode.setOnClickListener {
                            copyToClipboard(context, extractedCode, context.getString(R.string.chat_code_copied))
                        }

                        binding.btnOpenInWorkspace.setOnClickListener {
                            onOpenWorkspaceWithSnippet(extractedCode, lang ?: "js")
                        }
                    } else {
                        binding.layoutCodeBlock.visibility = View.GONE
                    }
                }

                binding.btnCopyMessage.setOnClickListener {
                    copyToClipboard(context, item.content, context.getString(R.string.chat_message_copied))
                }

                // Retry button for errors or cancelled states
                if (item.status == "error" || item.status == "cancelled") {
                    binding.btnRetryMessage.visibility = View.VISIBLE
                    binding.btnRetryMessage.setOnClickListener {
                        onRetry?.invoke(item)
                    }
                } else {
                    binding.btnRetryMessage.visibility = View.GONE
                }

                // Retry button for errors or cancelled states
                if (item.status == "error" || item.status == "cancelled") {
                    binding.btnRetryMessage.visibility = View.VISIBLE
                    binding.btnRetryMessage.setOnClickListener {
                        onRetry?.invoke(item)
                    }
                } else {
                    binding.btnRetryMessage.visibility = View.GONE
                }

                // If error is missing API key, show shortcut button
                if (item.content.contains("API key", ignoreCase = true) || item.status == "error") {
                    binding.btnConfigureMissingKey.visibility = View.VISIBLE
                    binding.btnConfigureMissingKey.setOnClickListener {
                        onConfigureKey(item.content)
                    }
                } else {
                    binding.btnConfigureMissingKey.visibility = View.GONE
                }
            }
        }

        private fun copyToClipboard(context: Context, text: String, toastMsg: String) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("ZIP_BUG", text))
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        }

        private fun parseMarkdownCodeBlocks(content: String, existingCode: String?, existingLang: String?): Triple<String, String?, String?> {
            if (!existingCode.isNullOrBlank()) {
                return Triple(content, existingCode, existingLang)
            }

            val pattern = Regex("```([a-zA-Z0-9_-]*)\\s*\\n?([\\s\\S]*?)```")
            val match = pattern.find(content)
            return if (match != null) {
                val lang = match.groupValues[1].ifBlank { "code" }
                val code = match.groupValues[2].trimEnd()
                val textWithoutCode = content.replace(match.value, "").trim()
                Triple(textWithoutCode.ifBlank { "Generated Code:" }, code, lang)
            } else {
                Triple(content, null, null)
            }
        }
    }
}
