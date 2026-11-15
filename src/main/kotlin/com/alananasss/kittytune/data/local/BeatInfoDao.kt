package com.alananasss.kittytune.data.local

import java.sql.ResultSet

class BeatInfoDao(private val db: AppDatabase) {

    private fun row(rs: ResultSet): BeatInfoEntity {
        return BeatInfoEntity(
            songId = rs.getString("songId"),
            bpm = rs.getFloat("bpm"),
            firstBeatOffsetMs = rs.getLong("firstBeatOffsetMs"),
            confidence = rs.getFloat("confidence"),
            analyzedAt = rs.getLong("analyzedAt"),
            mixInPointMs = rs.getLong("mixInPointMs").let { if (rs.wasNull()) null else it },
            mixOutPointMs = rs.getLong("mixOutPointMs").let { if (rs.wasNull()) null else it },
            keyPitchClass = rs.getInt("keyPitchClass").let { if (rs.wasNull()) null else it },
            keyIsMinor = rs.getInt("keyIsMinor").let { if (rs.wasNull()) null else it == 1 },
            energy = rs.getFloat("energy").let { if (rs.wasNull()) null else it },
            analysisVersion = rs.getInt("analysisVersion").let { if (rs.wasNull()) 1 else it },
        )
    }

    suspend fun upsert(beatInfo: BeatInfoEntity) {
        db.execSilent(
            """INSERT OR REPLACE INTO beat_info(
                songId, bpm, firstBeatOffsetMs, confidence, analyzedAt,
                mixInPointMs, mixOutPointMs, keyPitchClass, keyIsMinor, energy, analysisVersion
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            beatInfo.songId,
            beatInfo.bpm,
            beatInfo.firstBeatOffsetMs,
            beatInfo.confidence,
            beatInfo.analyzedAt,
            beatInfo.mixInPointMs,
            beatInfo.mixOutPointMs,
            beatInfo.keyPitchClass,
            beatInfo.keyIsMinor?.let { if (it) 1 else 0 },
            beatInfo.energy,
            beatInfo.analysisVersion,
        )
    }

    suspend fun getBeatInfo(songId: String): BeatInfoEntity? =
        db.queryOne("SELECT * FROM beat_info WHERE songId = ? LIMIT 1", songId, mapper = ::row)

    /** The rows of every song in [songIds] that has one, in no particular order. */
    suspend fun getBeatInfoForSongs(songIds: List<String>): List<BeatInfoEntity> {
        if (songIds.isEmpty()) return emptyList()
        // SQLite caps the number of bound parameters; chunk to stay well under it.
        return songIds.distinct().chunked(500).flatMap { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.query("SELECT * FROM beat_info WHERE songId IN ($marks)", *chunk.toTypedArray(), mapper = ::row)
        }
    }

    suspend fun deleteBeatInfo(songId: String) =
        db.execSilent("DELETE FROM beat_info WHERE songId = ?", songId)

    suspend fun clearAll() =
        db.execSilent("DELETE FROM beat_info")

    suspend fun clearFailedBeatInfo() =
        db.execSilent("DELETE FROM beat_info WHERE bpm <= 0")
}
