package com.zipbug.base.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val model: String = "openrouter/free",
    val provider: String = "OPENROUTER",
    val isPinned: Boolean = false
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent",
    val codeSnippet: String? = null,
    val codeLanguage: String? = null,
    val toolAttachment: String? = null
)
