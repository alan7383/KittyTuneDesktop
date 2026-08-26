package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turns merged events into the local data they describe (issue #33).
 *
 * The log is the record of what happened; this is what makes it visible. Two independent things keep a
 * listen from being counted twice: [SyncLog.merge] has already dropped everything known, and the row
 * carries the event's id under a unique index, so even a merge that runs twice over the same batch —
 * cleared marks, a restored backup, a peer that resends — produces one row.
 *
 * A kind this version does not know is ignored rather than an error. Two devices on different versions
 * still sync everything they have in common, and the unknown events stay in the log to be understood
 * after an update.
 */
object SyncApply {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Re-applies anything the log holds that the statistics table is missing (issue #33).
     *
     * This exists because the two could get out of step, and did — badly. [SyncLog.merge] writes the events
     * and advances the marks; inserting the rows is a separate step afterwards. When that step failed or was
     * cancelled, the marks had already moved: the device believed it held those listens, the peer would never
     * send them again, and the rows were simply gone.
     *
     * Measured on a real pair before this was added: a phone whose log held 71 listens had **5** of them in
     * its table. Sixty-six listens acknowledged and lost.
     *
     * The fix is to stop treating the table as the record and treat it as what it always was — a projection
     * of the log. Reconciling is cheap and idempotent: every row carries its event's id under a unique index,
     * so re-inserting what is already there does nothing.
     *
     * @return how many rows this restored.
     */
    suspend fun reconcile(): Int {
        val events = runCatching { SyncLog.all() }.getOrDefault(emptyList())
            .filter { it.kind == SyncKinds.LISTEN }
        if (events.isEmpty()) return 0
        val rows = events.mapNotNull { toRow(it) }
        val restored = runCatching { insertRows(rows) }.getOrDefault(0)
        if (restored > 0) ListeningStatsRepository.onStatsChanged()
        return restored
    }

    fun apply(events: List<SyncEvent>) {
        if (events.isEmpty()) return
        scope.launch { applyNow(events) }
    }

    /**
     * The same work, awaited.
     *
     * The exchange reports how many events it applied, and a caller that launched the writes and
     * returned immediately was reporting a number for work that had not happened — so the screen could
     * say "12 received" while the statistics still showed none of them (issue #33).
     */
    suspend fun applyNow(events: List<SyncEvent>) {
        if (events.isEmpty()) return

        // Collected first and written in one transaction. A first pairing carries hundreds of rows, and
        // one commit each — with autocommit, that is what a loop of single inserts means — turns a moment
        // into a visible pause (issue #33).
        val listens = events
            .filter { it.kind == SyncKinds.LISTEN }
            .mapNotNull { event -> toRow(event) }

        val inserted = runCatching { insertRows(listens) }.getOrDefault(0)

        // One notification for the batch rather than one per row: each would otherwise invalidate the
        // cache and wake every screen watching it.
        if (inserted > 0) ListeningStatsRepository.onStatsChanged()
    }

    /** One transaction, and the count of rows that were genuinely new. */
    private suspend fun insertRows(rows: List<ListeningStatsEvent>): Int =
        if (rows.isEmpty()) 0 else AppDatabase.downloadDao.insertStatsEvents(rows)

    private fun toRow(event: SyncEvent): ListeningStatsEvent? {
        val payload = runCatching {
            gson.fromJson(event.payload, ListenPayload::class.java)
        }.getOrNull() ?: return null

        return ListeningStatsEvent(
            trackId = payload.trackId,
            trackTitle = payload.trackTitle,
            artistName = payload.artistName,
            artistId = payload.artistId,
            artistPermalink = payload.artistPermalink,
            artistAvatarUrl = payload.artistAvatarUrl,
            // The table requires an artwork URL; a peer may have had none.
            artworkUrl = payload.artworkUrl.orEmpty(),
            source = payload.source,
            eventType = payload.eventType,
            listenDurationMs = payload.listenDurationMs,
            trackDurationMs = payload.trackDurationMs,
            // The peer's clock, not ours: a listen belongs to the moment it happened, which is what puts
            // it in the right week on both devices.
            timestamp = event.timestampMs,
            furthestPositionMs = payload.furthestPositionMs,
            // What makes this insert idempotent at the database level rather than at ours.
            syncEventId = event.id,
        )
    }
}
