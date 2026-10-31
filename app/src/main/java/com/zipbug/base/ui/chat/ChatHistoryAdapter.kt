package com.zipbug.base.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.zipbug.base.R
import com.zipbug.base.data.ChatSessionEntity
import com.zipbug.base.databinding.ItemChatHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatHistoryAdapter(
    private val onSelectSession: (ChatSessionEntity) -> Unit,
    private val onRenameSession: (ChatSessionEntity) -> Unit,
    private val onTogglePin: (ChatSessionEntity) -> Unit,
    private val onDeleteSession: (ChatSessionEntity) -> Unit
) : RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder>() {

    private val sessions = mutableListOf<ChatSessionEntity>()
    private val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

    fun submitList(list: List<ChatSessionEntity>) {
        sessions.clear()
        sessions.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemChatHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    inner class HistoryViewHolder(private val binding: ItemChatHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatSessionEntity) {
            binding.tvHistoryTitle.text = item.title
            binding.tvHistoryMeta.text = "${item.model} • ${dateFormat.format(Date(item.updatedAt))}"
            binding.ivHistoryPin.visibility = if (item.isPinned) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onSelectSession(item)
            }

            binding.btnHistoryMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, if (item.isPinned) "Unpin" else "Pin")
                popup.menu.add(0, 2, 1, "Rename")
                popup.menu.add(0, 3, 2, "Delete")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> onTogglePin(item)
                        2 -> onRenameSession(item)
                        3 -> onDeleteSession(item)
                    }
                    true
                }
                popup.show()
            }
        }
    }
}
