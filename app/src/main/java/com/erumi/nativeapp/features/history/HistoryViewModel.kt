package com.erumi.nativeapp.features.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.erumi.nativeapp.data.repository.UserLibraryRepository
import com.erumi.nativeapp.domain.model.WatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val historyItems: List<WatchProgress> = emptyList()
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val libraryRepo = UserLibraryRepository(application)
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepo.watchHistoryFlow.collect { list ->
                _uiState.update { it.copy(historyItems = list) }
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            libraryRepo.clearHistory()
        }
    }
}
