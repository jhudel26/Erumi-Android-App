package com.erumi.nativeapp.features.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.erumi.nativeapp.core.datastore.UserPreferencesDataStore
import com.erumi.nativeapp.core.download.ErumiDownloadUtil
import com.erumi.nativeapp.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val cacheClearedMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesDataStore = UserPreferencesDataStore(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesDataStore.userSettingsFlow.collect { s ->
                _uiState.update { it.copy(settings = s) }
                ErumiDownloadUtil.updateRequirements(getApplication(), s.wifiOnlyDownloads)
            }
        }
    }

    fun setPreferredQuality(quality: String) {
        viewModelScope.launch { preferencesDataStore.updatePreferredQuality(quality) }
    }

    fun setAutoNext(autoNext: Boolean) {
        viewModelScope.launch { preferencesDataStore.updateAutoNextEpisode(autoNext) }
    }

    fun setDefaultAudio(audio: String) {
        viewModelScope.launch { preferencesDataStore.updateDefaultAudio(audio) }
    }

    fun setSkipIntro(skip: Boolean) {
        viewModelScope.launch { preferencesDataStore.updateSkipIntro(skip) }
    }

    fun setWifiOnlyDownloads(wifiOnly: Boolean) {
        viewModelScope.launch {
            preferencesDataStore.updateWifiOnlyDownloads(wifiOnly)
            ErumiDownloadUtil.updateRequirements(getApplication(), wifiOnly)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearImageCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val imageLoader = Coil.imageLoader(getApplication())
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            _uiState.update { it.copy(cacheClearedMessage = "Image cache cleared successfully!") }
        }
    }

    fun dismissCacheMessage() {
        _uiState.update { it.copy(cacheClearedMessage = null) }
    }
}
