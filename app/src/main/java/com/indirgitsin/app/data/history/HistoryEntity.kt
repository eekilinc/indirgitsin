package com.indirgitsin.app.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val lastQuality: String? = null
)
