package com.alananasss.kittytune.data.discord

import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import java.util.Locale
import kotlin.math.abs

/**
 * Builds a concise summary of active playback speed/pitch, audio effects, and equalizer
 * for Discord Rich Presence (e.g. "Slowed (0.85x) • Bass Boost").
 */
internal fun summarizeDiscordEffects(
    effects: AudioEffectsState,
    equalizer: EqualizerState,
): String? {
    val tags = mutableListOf<String>()

    val speedDelta = abs(effects.speed - 1f)
    var reverbCombined = false
    if (speedDelta > 0.02f) {
        val speedLabel = formatSpeed(effects.speed)
        when {
            !effects.isPitchEnabled -> tags += "$speedLabel (Pitch Lock)"
            effects.speed < 1f && effects.isReverbEnabled -> {
                tags += "Slowed + Reverb ($speedLabel)"
                reverbCombined = true
            }
            effects.speed < 1f -> tags += "Slowed ($speedLabel)"
            effects.speed >= 1.15f -> tags += "Nightcore ($speedLabel)"
            else -> tags += "Sped Up ($speedLabel)"
        }
    }

    if (effects.isBassBoostEnabled) tags += "Bass Boost"
    if (effects.isReverbEnabled && !reverbCombined) tags += "Reverb"
    if (effects.isShimmerReverbEnabled) tags += "Shimmer Reverb"
    if (effects.is8DEnabled) tags += "8D Audio"
    if (effects.isMuffledEnabled) tags += "Muffled"
    if (effects.isRainEnabled) tags += formatAmbient(effects.ambientType)
    if (effects.isVinylLoFiEnabled) tags += "Vinyl Lo-Fi"
    if (effects.isSuperWideEnabled) tags += "Super Wide"
    if (effects.isUnderwaterEnabled) tags += "Underwater"
    if (effects.isNightDriveEnabled) tags += "Night Drive"
    if (effects.isVocalRemoverEnabled) tags += "Instrumental"
    if (effects.isVocalBoostEnabled) tags += "Vocal Boost"
    if (effects.isEarrapeEnabled) tags += "Overdrive"
    if (effects.isMonoEnabled) tags += "Mono"
    if (effects.isStadiumEnabled) tags += "Stadium"
    if (effects.isEmptyMallEnabled) tags += "Empty Mall"
    if (effects.isWalkmanEnabled) tags += "Walkman"
    if (effects.isGramophoneEnabled) tags += "Gramophone"
    if (effects.isVintageMp3Enabled) tags += "Vintage MP3"
    if (effects.isFlangerEnabled) tags += "Flanger"
    if (effects.isPhaserEnabled) tags += "Phaser"
    if (effects.isChorusEnabled) tags += "Chorus"
    if (effects.isPingPongDelayEnabled) tags += "Ping-Pong Delay"
    if (effects.isReverseEchoEnabled) tags += "Reverse Echo"
    if (effects.isChiptuneEnabled) tags += "Chiptune"
    if (effects.isRotarySpeakerEnabled) tags += "Rotary Speaker"
    if (effects.isTapeSaturationEnabled) tags += "Tape Saturation"
    if (effects.isSubOctaverEnabled) tags += "Sub-Octaver"
    if (effects.isTranceGateEnabled) tags += "Trance Gate"
    if (effects.isPartyNextDoorEnabled) tags += "PND Vibe"
    if (effects.isMegaphoneEnabled) tags += "Megaphone"
    if (effects.isRobotVocoderEnabled) tags += "Vocoder"
    if (effects.isAsmrVocalEnabled) tags += "ASMR Vocal"
    if (effects.isNormalizationEnabled) tags += "Normalized"

    if (equalizer.isEnabled) {
        val preset = equalizer.selectedPreset.trim()
        tags += if (preset.isNotEmpty() && !preset.equals("Flat", ignoreCase = true)) {
            "EQ ($preset)"
        } else {
            "EQ"
        }
    }

    if (tags.isEmpty()) return null
    return if (tags.size <= 2) {
        tags.joinToString(" • ")
    } else {
        "${tags[0]} • ${tags[1]} +${tags.size - 2}"
    }
}

private fun formatSpeed(speed: Float): String {
    val rounded = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "${rounded}x"
}

private fun formatAmbient(ambientType: String): String = when (ambientType.lowercase(Locale.US)) {
    "fireplace" -> "Fireplace"
    "cafe" -> "Café"
    "wind" -> "Wind"
    "ocean", "waves" -> "Ocean"
    "thunder" -> "Thunder"
    else -> "Rain"
}
