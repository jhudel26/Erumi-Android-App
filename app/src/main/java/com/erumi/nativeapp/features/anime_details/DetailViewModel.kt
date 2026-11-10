package com.erumi.nativeapp.features.anime_details

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.core.datastore.UserPreferencesDataStore
import com.erumi.nativeapp.core.utils.FuzzyMatcher
import com.erumi.nativeapp.data.repository.AnimeRepository
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.domain.model.Anime
import com.erumi.nativeapp.domain.model.DownloadItem
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.domain.model.Episode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class DetailUiState(
    val anime: Anime? = null,
    val episodes: List<Episode> = emptyList(),
    val downloadsMap: Map<Int, DownloadItem> = emptyMap(),
    val isInWatchlist: Boolean = false,
    val lastWatchedEpisode: Int? = null,
    val lastWatchedPositionMs: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedChunk: Int = 0
)

class DetailViewModel(
    application: Application,
    private val animeTitle: String
) : AndroidViewModel(application) {

    private val animeRepo = AnimeRepository(application) // A3: pass context so ImageResolver is initialized
    private val libraryRepo = UserLibraryRepository(application)
    private val preferencesDataStore = UserPreferencesDataStore(application)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    // Persistent collectors launched once - cancelled and re-launched on loadDetails() retry (B6/A7)
    private var watchlistCollectorJob: Job? = null
    private var continueWatchingJob: Job? = null
    private var downloadsJob: Job? = null

    init {
        loadDetails()
    }

    fun loadDetails() {
        // Cancel stale collectors before re-launching (B6/A7 - prevents accumulation on retry)
        watchlistCollectorJob?.cancel()
        continueWatchingJob?.cancel()
        downloadsJob?.cancel()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1. Fetch detailed metadata for art/synopsis only.
            // Playback identity MUST stay the user-selected title (same as Erumi streaming android).
            // AniList SEARCH_MATCH can return a different long isekai title that only shares
            // weak words ("magic"/"strongest") - never let that replace the play title.
            val metadata = animeRepo.getAnimeDetails(animeTitle)
            val baseAnime = mergeMetadataPreservingSelectedTitle(animeTitle, metadata)

            _uiState.update { it.copy(anime = baseAnime) }

            // 2. Episode list: prefer HiAnime (via english/romaji/synonyms). Pass aired
            // count from AniList only as a last-resort hint - never invent 12 eps.
            try {
                val eps = animeRepo.getEpisodes(
                    animeTitle,
                    totalEpisodes = baseAnime.episodes.takeIf { it > 0 }
                )
                _uiState.update { it.copy(episodes = eps, isLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }

            // 3. Observe watchlist status (persistent, cancelled on retry)
            watchlistCollectorJob = viewModelScope.launch {
                libraryRepo.isInWatchlistFlow(baseAnime.id).collectLatest { inWatchlist ->
                    _uiState.update { it.copy(isInWatchlist = inWatchlist) }
                }
            }
        }

        // 4. Check continue watching state for this anime
        continueWatchingJob = viewModelScope.launch {
            libraryRepo.continueWatchingFlow.collect { list ->
                val progress = list.firstOrNull { it.animeTitle.equals(animeTitle, ignoreCase = true) }
                if (progress != null) {
                    _uiState.update {
                        it.copy(
                            lastWatchedEpisode = progress.episodeNumber,
                            lastWatchedPositionMs = progress.positionMs
                        )
                    }
                }
            }
        }

        // 5. Observe download statuses for episodes of this anime
        downloadsJob = viewModelScope.launch {
            libraryRepo.downloadsFlow.collect { downloads ->
                val filtered = downloads
                    .filter { it.animeTitle.equals(animeTitle, ignoreCase = true) }
                    .associateBy { it.episodeNumber }
                _uiState.update { current ->
                    val resolvedEpisodes = if (current.episodes.isEmpty() && filtered.isNotEmpty()) {
                        filtered.keys.sorted().map { epNum ->
                            Episode(episodeNumber = epNum, title = "Episode $epNum")
                        }
                    } else {
                        current.episodes
                    }
                    current.copy(downloadsMap = filtered, episodes = resolvedEpisodes, isLoading = false)
                }
            }
        }
    }

    fun toggleWatchlist() {
        val currentAnime = _uiState.value.anime ?: return
        viewModelScope.launch {
            libraryRepo.toggleWatchlist(currentAnime)
        }
    }

    fun downloadEpisode(episodeNumber: Int) {
        val currentAnime = _uiState.value.anime ?: return
        val existing = _uiState.value.downloadsMap[episodeNumber]
        if (existing?.status == DownloadStatus.COMPLETED || existing?.status == DownloadStatus.DOWNLOADING) {
            return
        }

        // Immediate visual response: switch from FAILED/idle to PENDING
        val tempDownloads = _uiState.value.downloadsMap.toMutableMap()
        tempDownloads[episodeNumber] = DownloadItem(
            id = "${currentAnime.id}_$episodeNumber",
            animeId = currentAnime.id,
            animeTitle = currentAnime.title,
            episodeNumber = episodeNumber,
            poster = currentAnime.poster,
            streamUrl = "",
            status = DownloadStatus.PENDING,
            progress = 0
        )
        _uiState.update { it.copy(downloadsMap = tempDownloads) }

        viewModelScope.launch {
            val audioPref = try { preferencesDataStore.userSettingsFlow.first().defaultAudio } catch (e: Exception) { "sub" }
            val stream = animeRepo.resolveStream(animeTitle, episodeNumber, audioPref)
            if (stream == null) {
                val failedDownloads = _uiState.value.downloadsMap.toMutableMap()
                failedDownloads[episodeNumber] = DownloadItem(
                    id = "${currentAnime.id}_$episodeNumber",
                    animeId = currentAnime.id,
                    animeTitle = currentAnime.title,
                    episodeNumber = episodeNumber,
                    poster = currentAnime.poster,
                    streamUrl = "",
                    status = DownloadStatus.FAILED,
                    error = "Stream not available"
                )
                _uiState.update { it.copy(downloadsMap = failedDownloads) }
                return@launch
            }

            libraryRepo.startDownload(
                animeId = currentAnime.id,
                animeTitle = currentAnime.title,
                poster = currentAnime.poster,
                episodeNumber = episodeNumber,
                streamUrl = stream.url,
                referer = stream.referer,
                subtitles = stream.subtitles
            )
        }
    }

    fun deleteDownload(episodeNumber: Int) {
        val currentAnime = _uiState.value.anime ?: return
        val downloadId = "${currentAnime.id}_$episodeNumber"
        viewModelScope.launch {
            libraryRepo.deleteDownload(downloadId)
        }
    }

    fun selectChunk(index: Int) {
        _uiState.update { it.copy(selectedChunk = index) }
    }

    /**
     * Keep [selectedTitle] as the series identity for play/download/history.
     * Only adopt AniList names when they validate as the same series.
     * Romaji/synonyms are kept when English validates - HiAnime needs those keys.
     */
    private fun mergeMetadataPreservingSelectedTitle(selectedTitle: String, metadata: Anime?): Anime {
        if (metadata == null) {
            return Anime(id = "temp_${selectedTitle.hashCode()}", title = selectedTitle)
        }
        val nameCandidates = listOfNotNull(
            metadata.englishName,
            metadata.romajiName,
            metadata.title
        ) + metadata.synonyms
        val isSameSeries = nameCandidates.any { FuzzyMatcher.isValidTitleMatch(selectedTitle, it) }
        return if (isSameSeries) {
            metadata.copy(title = selectedTitle)
        } else {
            // Wrong AniList hit - keep posters/synopsis empty rather than swap series.
            Anime(id = "temp_${selectedTitle.hashCode()}", title = selectedTitle)
        }
    }

    fun markEpisodeAsWatched(episodeNumber: Int) {
        val currentAnime = _uiState.value.anime ?: return
        viewModelScope.launch {
            // 24 minutes nominal episode duration in ms
            val nominalDurationMs = 24 * 60 * 1000L
            libraryRepo.saveWatchProgress(
                animeId = currentAnime.id,
                animeTitle = animeTitle,
                poster = currentAnime.poster,
                episodeNumber = episodeNumber,
                positionMs = nominalDurationMs,
                durationMs = nominalDurationMs
            )
        }
    }
}