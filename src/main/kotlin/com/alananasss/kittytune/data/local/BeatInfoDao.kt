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
        )
    }

    suspend fun upsert(beatInfo: BeatInfoEntity) {
        db.execSilent(
            """INSERT OR REPLACE INTO beat_info(
                songId, bpm, firstBeatOffsetMs, confidence, analyzedAt,
                mixInPointMs, mixOutPointMs, keyPitchClass, keyIsMinor
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            beatInfo.songId,
            beatInfo.bpm,
            beatInfo.firstBeatOffsetMs,
            beatInfo.confidence,
            beatInfo.analyzedAt,
            beatInfo.mixInPointMs,
            beatInfo.mixOutPointMs,
            beatInfo.keyPitchClass,
            beatInfo.keyIsMinor?.let { if (it) 1 else 0 }
        )
    }

    suspend fun getBeatInfo(songId: String): BeatInfoEntity? =
        db.queryOne("SELECT * FROM beat_info WHERE songId = ? LIMIT 1", songId, mapper = ::row)

    suspend fun deleteBeatInfo(songId: String) =
        db.execSilent("DELETE FROM beat_info WHERE songId = ?", songId)

    suspend fun clearAll() =
        db.execSilent("DELETE FROM beat_info")

    suspend fun clearFailedBeatInfo() =
        db.execSilent("DELETE FROM beat_info WHERE bpm <= 0")
}
