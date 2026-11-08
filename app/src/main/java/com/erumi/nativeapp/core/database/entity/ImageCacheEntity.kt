package com.erumi.nativeapp.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "image_cache")
data class ImageCacheEntity(
    @PrimaryKey val animeId: String,
    val resolvedPosterUrl: String?,
    val resolvedBannerUrl: String?,
    val source: String?, // "allanime", "anilist", "placeholder"
    val resolvedAt: Long = System.currentTimeMillis()
)
