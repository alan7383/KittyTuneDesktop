package com.alananasss.kittytune.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.ReportPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * The statistics screen's state: one [ListeningReport] for the selected span.
 *
 * Follows [ListeningStatsRepository.revision], so a listen finishing — or a sync landing — while the screen is
 * open shows up without leaving it. A load still in flight is cancelled by the next one, so flicking through
 * the periods always settles on the one that is selected.
 */
class ListeningStatsViewModel : ViewModel() {

    var period by mutableStateOf(ReportPeriod.WEEK)
        private set
    var periodOffset by mutableStateOf(0)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var report by mutableStateOf<ListeningReport?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set

    /** The span's listens, newest first, for the "every play" list. */
    var events by mutableStateOf<List<ListeningStatsEvent>>(emptyList())
        private set

    private var oldestEventMs: Long? = null
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // drop(1): the current value is what the first load already used.
            ListeningStatsRepository.revision.drop(1).collect { load() }
        }
    }

    fun selectPeriod(value: ReportPeriod) {
        if (value == period && periodOffset == 0) return
        period = value
        periodOffset = 0
        load()
    }

    fun previousPeriod() {
        if (period == ReportPeriod.ALL_TIME || !canGoBack) return
        periodOffset--
        load()
    }

    fun nextPeriod() {
        if (period == ReportPeriod.ALL_TIME || !canGoForward) return
        periodOffset++
        load()
    }

    fun resetToCurrentPeriod() {
        if (periodOffset == 0) return
        periodOffset = 0
        load()
    }

    private fun load() {
        val selected = period
        val offset = periodOffset
        loadJob?.cancel()
        isLoading = report == null || report?.window == null
        loadJob = viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            if (oldestEventMs == null) {
                oldestEventMs = ListeningStatsRepository.getSnapshot(0L).firstAtMs
            }
            val firstEventMs = oldestEventMs
            val window = ListeningReports.windowFor(selected, now, zone, firstEventMs, offset)
            val previous = ListeningReports.previousWindow(selected, window, zone)
            val rows = ListeningStatsRepository.getEvents(previous?.startMs ?: window.startMs)
            val built = withContext(Dispatchers.Default) {
                val previousMs = previous?.let { span ->
                    rows.filter { it.timestamp >= span.startMs && it.timestamp < span.endMs }.sumOf { it.listenDurationMs }
                }
                ListeningReports.build(selected, window, rows, previousMs, zone)
            }
            events = rows.filter { it.timestamp >= window.startMs && it.timestamp < window.endMs }
                .sortedByDescending { it.timestamp }
            report = built
            canGoForward = selected != ReportPeriod.ALL_TIME && offset < 0
            canGoBack = selected != ReportPeriod.ALL_TIME && (firstEventMs == null || window.endMs > firstEventMs || offset > -52)
            isLoading = false
        }
    }
}
