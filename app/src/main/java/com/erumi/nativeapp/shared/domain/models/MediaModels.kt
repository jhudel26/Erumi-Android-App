package com.erumi.nativeapp.shared.domain.models

/**
 * Common abstraction for media progress across Anime watching and Manga/Manhua reading.
 */
interface MediaProgress {
    val mediaId: String
    val mediaTitle: String
    val mediaCoverUrl: String?
    val mediaSubtitle: String
    val progressFraction: Float // 0f..1f
    val progressPercentage: Int // 0..100
    val lastUpdated: Long
}

/**
 * Common abstraction for media episodes or chapters.
 */
interface MediaChapter {
    val chapterId: String
    val chapterName: String
    val chapterNumber: Float?
    val isReadOrWatched: Boolean
    val isOfflineAvailable: Boolean
}
