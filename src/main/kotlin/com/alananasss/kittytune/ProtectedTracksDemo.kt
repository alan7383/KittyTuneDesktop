package com.alananasss.kittytune

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.drm.ProtectedTracksNotice
import com.alananasss.kittytune.data.local.PlayerPreferences

/** Only enabled by the Gradle demo task, which supplies its own data and cache directories. */
internal object ProtectedTracksDemo {
    val enabled = System.getProperty("kittytune.protectedTracksDemo") == "true"
    var cardRevision by mutableIntStateOf(0)
        private set

    private fun resetCard() {
        listOf("protected_tracks_enabled", "protected_tracks_announced", "protected_tracks_hint_shown", "drm_onboarding_done").forEach(Prefs::remove)
        cardRevision++
    }

    fun prepare() {
        if (!enabled) return
        check(System.getenv("APPDATA")?.contains("protected-tracks-demo") == true) {
            "The protected tracks demo requires an isolated profile"
        }
        resetCard()
        PlayerPreferences().setHasCompletedSetup(true)
        TokenManager.setGuestMode(true)
        com.alananasss.kittytune.ui.pet.PetConfig.markAnnounced()
    }

    @Composable
    fun Controls() {
        Surface(shape = RoundedCornerShape(20.dp)) {
            FlowRow {
                TextButton(onClick = { resetCard() }) { Text("Démo : carte") }
                TextButton(onClick = {
                    Prefs.remove("protected_tracks_enabled")
                    Prefs.remove("protected_tracks_hint_shown")
                    ProtectedTracksNotice.onProtectedTrackSkipped()
                }) { Text("Proposer l’activation") }
                TextButton(onClick = { ProtectedTracksNotice.onProtectedTrackUnavailable() }) { Text("Indisponible") }
                TextButton(onClick = { Toaster.show("Notification de test : les messages de KittyTune sont maintenant visibles.") }) { Text("Notification") }
            }
        }
    }
}
