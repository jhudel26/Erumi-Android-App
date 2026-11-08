package com.erumi.nativeapp.features.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.erumi.nativeapp.core.datastore.UserPreferencesDataStore
import com.erumi.nativeapp.core.download.ErumiDownloadUtil
import com.erumi.nativeapp.core.network.ErumiDataSourceFactory
import com.erumi.nativeapp.data.repository.AnimeRepository
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.domain.model.DownloadStatus
import com.erumi.nativeapp.domain.model.Episode
import com.erumi.nativeapp.domain.model.StreamSource
import com.erumi.nativeapp.domain.model.SubtitleTrack
import com.erumi.nativeapp.domain.model.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val animeTitle: String,
    val currentEpisodeNumber: Int,
    val totalEpisodes: Int = 12,
    val currentStream: StreamSource? = null,
    val availableSubtitles: List<SubtitleTrack> = emptyList(),
    val selectedSubtitleLang: String = "en",
    val selectedAudio: String = "sub",
    val playbackSpeed: Float = 1.0f,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isControlsVisible: Boolean = true,
    val error: String? = null,
    val showSkipIntro: Boolean = false,
    val downloadStatus: DownloadStatus? = null,
    val showAutoPlayCountdown: Boolean = false
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    val animeTitle: String,
    initialEpisodeNumber: Int,
    private val resumeTimeMs: Long = 0L,
    val animeId: String? = null,
    val initialPoster: String? = null
) : AndroidViewModel(application) {

    val resolvedAnimeId: String = if (!animeId.isNullOrBlank()) animeId else "anime_${animeTitle.hashCode()}"

    private val animeRepo = AnimeRepository()
    private val libraryRepo = UserLibraryRepository(application)
    private val preferencesDataStore = UserPreferencesDataStore(application)

    private var userSettings: UserSettings = UserSettings()
    private var animePoster: String? = initialPoster

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            animeTitle = animeTitle,
            currentEpisodeNumber = initialEpisodeNumber
        )
    )

    // Stored separately so resumeTimeMs comparison is never affected by state updates
    private val initialEpisodeNumberForResume: Int = initialEpisodeNumber
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    private var progressTrackerJob: Job? = null
    private var allEpisodes: List<Episode> = emptyList()

    init {
        initPlayer()
        observePreferencesAndLoad(initialEpisodeNumber)
        observeDownloads()
    }

    private fun observePreferencesAndLoad(initialEp: Int) {
        viewModelScope.launch {
            try {
                userSettings = preferencesDataStore.userSettingsFlow.first()
                _uiState.update {
                    it.copy(
                        selectedAudio = userSettings.defaultAudio,
                        selectedSubtitleLang = userSettings.defaultSubtitleLang
                    )
                }
            } catch (e: Exception) {
                // Keep default
            }

            // Ensure we have a poster for watch progress & downloads
            if (animePoster.isNullOrBlank()) {
                val cached = libraryRepo.getPosterForAnime(resolvedAnimeId, animeTitle)
                if (!cached.isNullOrBlank()) {
                    animePoster = cached
                } else {
                    try {
                        val details = animeRepo.getAnimeDetails(animeTitle)
                        val poster = details?.poster
                        if (!poster.isNullOrBlank()) {
                            animePoster = poster
                        }
                    } catch (_: Exception) {
                        // Offline fallback
                    }
                }
            }

            loadEpisodesAndStream(initialEp)
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            libraryRepo.downloadsFlow.collect { list ->
                val cur = list.firstOrNull {
                    (it.id == "${resolvedAnimeId}_${_uiState.value.currentEpisodeNumber}" ||
                            (it.animeTitle.equals(animeTitle, ignoreCase = true) &&
                                    it.episodeNumber == _uiState.value.currentEpisodeNumber))
                }
                _uiState.update { it.copy(downloadStatus = cur?.status) }
            }
        }
    }

    fun ensurePlayerInitialized(): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing
        initPlayer()
        return exoPlayer!!
    }

    private fun initPlayer() {
        exoPlayer?.release()
        val player = ExoPlayer.Builder(getApplication())
            .setSeekForwardIncrementMs(10_000)
            .setSeekBackIncrementMs(10_000)
            .build()

        // Enable text tracks by default and prefer English
        // Important: Set these before loading any media
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage("en")
            // Do not set ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND (audio description) for subtitles
            .setPreferredVideoMimeTypes(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_AV1)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _uiState.update { it.copy(isBuffering = true) }
                    }
                    Player.STATE_READY -> {
                        _uiState.update {
                            it.copy(
                                isBuffering = false,
                                durationMs = player.duration.coerceAtLeast(0L)
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        _uiState.update { it.copy(isPlaying = false) }
                        handleAutoNext()
                    }
                    Player.STATE_IDLE -> {
                        _uiState.update { it.copy(isBuffering = false) }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        error = "Playback error: ${error.localizedMessage ?: "Source unavailable"}"
                    )
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val extracted = mutableListOf<SubtitleTrack>()
                for ((groupIndex, group) in tracks.groups.withIndex()) {
                    if (group.type == C.TRACK_TYPE_TEXT) {
                        val trackGroup = group.mediaTrackGroup
                        for (trackIndex in 0 until trackGroup.length) {
                            val format = trackGroup.getFormat(trackIndex)
                            val lang = format.language ?: "und"
                            val label = format.label ?: if (lang != "und") lang.uppercase() else "Subtitles ${extracted.size + 1}"
                            val isSelected = group.isTrackSelected(trackIndex)
                            extracted.add(
                                SubtitleTrack(
                                    label = label,
                                    url = "",
                                    language = lang,
                                    groupIndex = groupIndex,
                                    trackIndex = trackIndex,
                                    isSelected = isSelected
                                )
                            )
                        }
                    }
                }
                if (extracted.isNotEmpty()) {
                    _uiState.update { current ->
                        val merged = extracted.map { ext ->
                            val match = current.availableSubtitles.firstOrNull {
                                it.label.equals(ext.label, ignoreCase = true) ||
                                (it.language.isNotBlank() && it.language.equals(ext.language, ignoreCase = true))
                            }
                            if (match != null) {
                                ext.copy(label = match.label)
                            } else {
                                ext
                            }
                        }
                        val active = merged.firstOrNull { it.isSelected }
                        current.copy(
                            availableSubtitles = merged,
                            selectedSubtitleLang = active?.language ?: current.selectedSubtitleLang
                        )
                    }
                }
            }
        })

        exoPlayer = player
        startProgressTracker()
    }

    private fun loadEpisodesAndStream(episodeNumber: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBuffering = true, error = null, currentEpisodeNumber = episodeNumber) }

            // 1. Fetch episodes count (with offline tolerance)
            if (allEpisodes.isEmpty()) {
                try {
                    allEpisodes = animeRepo.getEpisodes(animeTitle)
                    if (allEpisodes.isNotEmpty()) {
                        _uiState.update { it.copy(totalEpisodes = allEpisodes.size) }
                    }
                } catch (_: Exception) {
                    _uiState.update { it.copy(totalEpisodes = it.totalEpisodes.coerceAtLeast(episodeNumber)) }
                }
            }

            // 2. Read audio preference
            val audioPref = _uiState.value.selectedAudio

            // 3. Resolve stream (Offline-first check: if downloaded, play directly from cache!)
            val downloaded = libraryRepo.getDownloadByEpisode(animeTitle, episodeNumber)
                ?: (animeId?.let { libraryRepo.getDownloadById("${it}_$episodeNumber") })
                ?: libraryRepo.getDownloadById("${resolvedAnimeId}_$episodeNumber")
            val stream = if (downloaded != null && downloaded.status == DownloadStatus.COMPLETED && downloaded.streamUrl.isNotBlank()) {
                val subs = if (downloaded.subtitles.isNotEmpty()) {
                    downloaded.subtitles
                } else {
                    try {
                        animeRepo.resolveStream(animeTitle, episodeNumber, audioPref)?.subtitles ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                StreamSource(
                    provider = "OfflineCache",
                    url = downloaded.streamUrl,
                    quality = "Offline",
                    audio = audioPref,
                    isHls = downloaded.streamUrl.contains(".m3u8", ignoreCase = true),
                    referer = ErumiDataSourceFactory.defaultRefererFor(downloaded.streamUrl),
                    subtitles = subs
                )
            } else {
                try {
                    animeRepo.resolveStream(animeTitle, episodeNumber, audioPref)
                } catch (_: Exception) {
                    null
                }
            }
            if (stream == null) {
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        error = "Could not resolve playable stream for Episode $episodeNumber."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    currentStream = stream,
                    // Reset subtitle list to what the new stream provides — onTracksChanged will append embedded ones
                    availableSubtitles = stream.subtitles,
                    selectedAudio = stream.audio
                )
            }

            // 4. Play in ExoPlayer
            // Only apply resumeTimeMs for the original episode that was opened, not for episode switches
            playStream(stream, if (episodeNumber == initialEpisodeNumberForResume) resumeTimeMs else 0L)
        }
    }

    private fun playStream(stream: StreamSource, startPosMs: Long) {
        val player = ensurePlayerInitialized()
        val referer = stream.referer.ifBlank { ErumiDataSourceFactory.defaultRefererFor(stream.url) }
        val dataSourceFactory = ErumiDownloadUtil.buildReadOnlyCacheDataSource(getApplication(), referer)

        // Build base MediaItem with URI
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(stream.url))

        // Attach external subtitle configurations to the MediaItem
        // This is the recommended approach for external subtitles
        if (stream.subtitles.isNotEmpty()) {
            val subtitleConfigs = stream.subtitles.mapNotNull { sub ->
                if (sub.url.isBlank()) return@mapNotNull null
                val mimeType = when {
                    sub.url.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                    sub.url.endsWith(".ttml", ignoreCase = true) || sub.url.endsWith(".xml", ignoreCase = true) -> MimeTypes.APPLICATION_TTML
                    else -> MimeTypes.TEXT_VTT
                }
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(mimeType)
                    .setLanguage(sub.language)
                    .setLabel(sub.label)
                    .setSelectionFlags(if (sub.language.equals("en", ignoreCase = true) || sub.label.contains("English", ignoreCase = true)) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            if (subtitleConfigs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }
        }

        val mediaItem = mediaItemBuilder.build()

        // Use DefaultMediaSourceFactory for better subtitle support
        // It automatically handles both HLS and regular streams with subtitles
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player.setMediaSource(mediaSource)
        player.prepare()

        if (startPosMs > 0) {
            player.seekTo(startPosMs)
        }

        player.playWhenReady = true
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.playbackState == Player.STATE_READY) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    val showIntro = userSettings.skipIntro && (pos in 5_000..90_000)

                    _uiState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            showSkipIntro = showIntro
                        )
                    }

                    // Save watch progress to Room with consistent animeId
                    if (pos > 5_000 && dur > 0) {
                        libraryRepo.saveWatchProgress(
                            animeId = resolvedAnimeId,
                            animeTitle = animeTitle,
                            poster = animePoster,
                            episodeNumber = _uiState.value.currentEpisodeNumber,
                            positionMs = pos,
                            durationMs = dur
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun seekForward() {
        exoPlayer?.seekForward()
    }

    fun seekBackward() {
        exoPlayer?.seekBack()
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleAudio() {
        val nextAudio = if (_uiState.value.selectedAudio == "sub") "dub" else "sub"
        _uiState.update { it.copy(selectedAudio = nextAudio) }
        loadEpisodesAndStream(_uiState.value.currentEpisodeNumber)
    }

    fun selectSubtitle(sub: SubtitleTrack) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)

        if (sub.language == "off" || (sub.groupIndex == -1 && sub.label.equals("Off", ignoreCase = true))) {
            // Disable all text tracks
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            _uiState.update { it.copy(selectedSubtitleLang = "off") }
        } else {
            // Enable text tracks
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val groups = player.currentTracks.groups
            if (sub.groupIndex in groups.indices) {
                val group = groups[sub.groupIndex]
                builder.setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, listOf(sub.trackIndex))
                )
            } else if (sub.language.isNotBlank() && sub.language != "und") {
                builder.setPreferredTextLanguage(sub.language)
            }
            _uiState.update { it.copy(selectedSubtitleLang = sub.language) }
        }
        player.trackSelectionParameters = builder.build()
    }

    fun selectSubtitle(language: String) {
        val match = _uiState.value.availableSubtitles.firstOrNull { it.language.equals(language, ignoreCase = true) }
        if (match != null) {
            selectSubtitle(match)
        } else {
            selectSubtitle(SubtitleTrack(label = if (language == "off") "Off" else language, url = "", language = language))
        }
    }

    fun skipIntro() {
        val player = exoPlayer ?: return
        player.seekTo(player.currentPosition + 85_000)
    }

    fun playNextEpisode() {
        ensurePlayerInitialized()
        val nextEp = _uiState.value.currentEpisodeNumber + 1
        loadEpisodesAndStream(nextEp)
    }

    fun playPrevEpisode() {
        ensurePlayerInitialized()
        val prevEp = (_uiState.value.currentEpisodeNumber - 1).coerceAtLeast(1)
        loadEpisodesAndStream(prevEp)
    }

    private fun handleAutoNext() {
        if (!userSettings.autoNextEpisode) return
        val cur = _uiState.value.currentEpisodeNumber
        if (cur < _uiState.value.totalEpisodes) {
            _uiState.update { it.copy(showAutoPlayCountdown = true) }
        }
    }

    fun cancelAutoPlay() {
        _uiState.update { it.copy(showAutoPlayCountdown = false) }
    }

    fun retryPlayback() {
        ensurePlayerInitialized()
        loadEpisodesAndStream(_uiState.value.currentEpisodeNumber)
    }

    fun releasePlayer() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    fun downloadCurrentEpisode() {
        val stream = _uiState.value.currentStream ?: return
        val epNum = _uiState.value.currentEpisodeNumber
        viewModelScope.launch {
            libraryRepo.startDownload(
                animeId = resolvedAnimeId,
                animeTitle = animeTitle,
                poster = animePoster,
                episodeNumber = epNum,
                streamUrl = stream.url,
                referer = stream.referer,
                subtitles = stream.subtitles
            )
        }
    }
}
