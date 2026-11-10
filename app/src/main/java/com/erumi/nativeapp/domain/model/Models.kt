package com.erumi.nativeapp.domain.model

data class Anime(
    val id: String,
    val title: String,
    val englishName: String? = null,
    val romajiName: String? = null,
    val nativeName: String? = null,
    val synonyms: List<String> = emptyList(),
    val poster: String? = null,
    val banner: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val releaseYear: Int? = null,
    val episodes: Int = 0,
    val score: Double? = null,
    val status: String? = null,
    val format: String? = null,
    val relations: List<AnimeRelation> = emptyList()
)

data class AnimeRelation(
    val relationType: String,
    val id: Int,
    val title: String,
    val format: String? = null,
    val year: Int? = null,
    val status: String? = null
)

data class Episode(
    val episodeNumber: Int,
    val epId: String = "",
    val title: String = "Episode $episodeNumber",
    val thumbnail: String? = null
)

data class SubtitleTrack(
    val label: String,
    val url: String,
    val language: String = "en",
    val groupIndex: Int = -1,
    val trackIndex: Int = -1,
    val isSelected: Boolean = false
) {
    // Unique identifier for selection: URL for external, groupIndex_trackIndex for embedded
    val id: String
        get() = if (url.isNotBlank()) url else "${groupIndex}_$trackIndex"
}

data class StreamSource(
    val provider: String,
    val server: String = "Default",
    val url: String,
    val directUrl: String = url,
    val quality: String = "Auto",
    val audio: String = "sub",
    val isHls: Boolean = true,
    val referer: String = "",
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class WatchProgress(
    val animeId: String,
    val animeTitle: String,
    val poster: String?,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressPercent: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

data class DownloadItem(
    val id: String,
    val animeId: String,
    val animeTitle: String,
    val episodeNumber: Int,
    val poster: String?,
    val streamUrl: String,
    val localFilePath: String? = null,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class UserSettings(
    val preferredQuality: String = "1080p",
    val autoNextEpisode: Boolean = true,
    val defaultAudio: String = "sub",
    val defaultSubtitleLang: String = "en",
    val subtitleSize: Float = 1.0f,
    val skipIntro: Boolean = true,
    val skipIntroSeconds: Int = 85,
    val wifiOnlyDownloads: Boolean = false,
    val appTheme: String = "Dark"
)
