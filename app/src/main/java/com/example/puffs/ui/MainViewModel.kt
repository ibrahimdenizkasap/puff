package com.example.puffs.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.puffs.data.Puff
import com.example.puffs.data.PuffRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.puffs.data.DraftSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

class MainViewModel(app: Application): AndroidViewModel(app) {
    private val repo = PuffRepository(app)

    // existing stats you already bind to UI
    val todayCount = repo.todayCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayPuffs = repo.todayPuffs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Puff>())

    val weekTotal = repo.last7DaysTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val allPuffs = repo.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Puff>())

    // NEW: finalized sessions list (newest first) for your Sessions/History pane
    val sessions = repo.sessionsDesc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // NEW: current (draft) session count shown in the UI
    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    init {
        // Auto-finalize any stale draft on app start, then load the current draft count
        viewModelScope.launch {
            repo.finalizeIfTimedOut()
            _sessionCount.value = repo.getDraftOnce()?.puffCount ?: 0
        }
    }

    private val _activeDraft = MutableStateFlow<DraftSession?>(null)
    val activeDraft: StateFlow<DraftSession?> = _activeDraft

    init {
        viewModelScope.launch {
            repo.finalizeIfTimedOut()
            _activeDraft.value = repo.getDraftOnce()
        }
    }

    fun addPuff() = viewModelScope.launch {
        repo.addPuff()
        _activeDraft.value = repo.getDraftOnce()
    }

    fun addMany(n: Int) = viewModelScope.launch {
        repeat(n) { repo.addPuff() }
        _activeDraft.value = repo.getDraftOnce()
    }

    fun undo(n: Int = 1) = viewModelScope.launch {
        repeat(n) { repo.undo() }
        _activeDraft.value = repo.getDraftOnce()
    }

    // 👇 this is what your UI calls
    fun endSessionNow() = viewModelScope.launch {
        repo.endSessionNow()
        _activeDraft.value = repo.getDraftOnce()
    }

    private val _currentSessionPuffs = MutableStateFlow<List<Puff>>(emptyList())
    val currentSessionPuffs = _currentSessionPuffs.asStateFlow()

    init {
        // already had finalizeIfTimedOut + initial draft load
        viewModelScope.launch {
            activeDraft.collectLatest { d ->
                _currentSessionPuffs.value = if (d != null) {
                    repo.puffsInRange(d.startTs, d.lastPuffTs)
                } else emptyList()
            }
        }
    }

    // MainViewModel.kt
    fun rebuildSessions() = viewModelScope.launch {
        repo.rebuildSessionsFromPuffs()
        _activeDraft.value = repo.getDraftOnce()   // refresh the live draft in UI
    }
}